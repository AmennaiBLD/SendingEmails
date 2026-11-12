import { logger } from "firebase-functions";
import { EmailContent, EventFacts } from "./templates";

/** A provider failure. `retryable` separates "try again later" from "this will never work". */
export class ProviderError extends Error {
  constructor(message: string, readonly retryable: boolean) {
    super(message);
  }
}

export interface EmailAttachment {
  filename: string;
  content: Buffer;
  contentType: string;
  /** When set, the attachment is inline and referenced from the HTML as `cid:<contentId>`. */
  contentId?: string;
}

export interface SendResult {
  providerMessageId: string;
}

export interface EmailProvider {
  readonly name: string;
  send(input: { to: string; content: EmailContent; attachments: EmailAttachment[]; idempotencyKey: string }): Promise<SendResult>;
}

export interface WhatsAppProvider {
  readonly name: string;
  /** Sends the approved ticket template with the ticket image in its media header. */
  sendTicket(input: { to: string; facts: EventFacts; ticketPng: Buffer }): Promise<SendResult>;
}

function failFor(status: number, detail: string): ProviderError {
  return new ProviderError(`HTTP ${status}: ${detail.slice(0, 300)}`, status === 429 || status >= 500);
}

async function post(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, { ...init, signal: AbortSignal.timeout(20_000) });
  } catch (error) {
    throw new ProviderError(`Network error: ${(error as Error).message}`, true);
  }
}

// ── Mock providers: local development and tests. Nothing leaves the machine. ──────────────

/** Recipients containing "+fail" fail permanently and "+flaky" fail temporarily, to exercise retries. */
function mockOutcome(recipient: string): void {
  if (recipient.includes("+fail") || recipient.endsWith("0000000")) throw new ProviderError("Mock provider: permanent failure", false);
  if (recipient.includes("+flaky") || recipient.endsWith("1111111")) throw new ProviderError("Mock provider: temporary failure", true);
}

export class MockEmailProvider implements EmailProvider {
  readonly name = "mock";
  async send(input: { to: string; content: EmailContent; attachments: EmailAttachment[] }): Promise<SendResult> {
    mockOutcome(input.to);
    // The text body carries no ticket credential (the QR travels as an attachment), so it is safe to log.
    logger.info("[mock email] not actually sent", {
      to: input.to, subject: input.content.subject, text: input.content.text,
      attachments: input.attachments.map((a) => `${a.filename} (${a.content.length} bytes)`),
    });
    return { providerMessageId: `mock-email-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` };
  }
}

export class MockWhatsAppProvider implements WhatsAppProvider {
  readonly name = "mock";
  async sendTicket(input: { to: string; facts: EventFacts; ticketPng: Buffer }): Promise<SendResult> {
    mockOutcome(input.to);
    logger.info("[mock whatsapp] not actually sent", { to: input.to, event: input.facts.eventTitle, imageBytes: input.ticketPng.length });
    return { providerMessageId: `mock-wa-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` };
  }
}

// ── Resend ────────────────────────────────────────────────────────────────────────────────

export class ResendEmailProvider implements EmailProvider {
  readonly name = "resend";
  constructor(private readonly apiKey: string, private readonly from: string) {}

  async send(input: { to: string; content: EmailContent; attachments: EmailAttachment[]; idempotencyKey: string }): Promise<SendResult> {
    if (!this.apiKey) throw new ProviderError("RESEND_API_KEY is not configured.", false);
    const response = await post("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        "Content-Type": "application/json",
        // The same key on every retry of one job: Resend will not send the message twice.
        "Idempotency-Key": input.idempotencyKey,
      },
      body: JSON.stringify({
        from: this.from,
        to: [input.to],
        subject: input.content.subject,
        html: input.content.html,
        text: input.content.text,
        attachments: input.attachments.map((a) => ({
          filename: a.filename,
          content: a.content.toString("base64"),
          content_type: a.contentType,
          ...(a.contentId ? { content_id: a.contentId } : {}),
        })),
      }),
    });
    const text = await response.text();
    if (!response.ok) throw failFor(response.status, text);
    const id = (JSON.parse(text) as { id?: string }).id;
    if (!id) throw new ProviderError("Resend returned no message id.", true);
    return { providerMessageId: id };
  }
}

// ── Meta WhatsApp Cloud API (official Business Platform) ──────────────────────────────────

export interface MetaWhatsAppConfig {
  accessToken: string;
  apiVersion: string;
  phoneNumberId: string;
  templateName: string;
  templateLanguage: string;
}

export class MetaWhatsAppCloudProvider implements WhatsAppProvider {
  readonly name = "meta";
  constructor(private readonly config: MetaWhatsAppConfig) {}

  private get base(): string {
    return `https://graph.facebook.com/${this.config.apiVersion}/${this.config.phoneNumberId}`;
  }

  async sendTicket(input: { to: string; facts: EventFacts; ticketPng: Buffer }): Promise<SendResult> {
    const { accessToken, phoneNumberId, templateName, templateLanguage } = this.config;
    if (!accessToken || !phoneNumberId) throw new ProviderError("WhatsApp Cloud API is not configured.", false);

    // 1. Upload the ticket image so the template's media header can carry the actual QR ticket.
    const form = new FormData();
    form.set("messaging_product", "whatsapp");
    form.set("type", "image/png");
    form.set("file", new Blob([new Uint8Array(input.ticketPng)], { type: "image/png" }), "ticket.png");
    const upload = await post(`${this.base}/media`, { method: "POST", headers: { Authorization: `Bearer ${accessToken}` }, body: form });
    const uploadText = await upload.text();
    if (!upload.ok) throw failFor(upload.status, uploadText);
    const mediaId = (JSON.parse(uploadText) as { id?: string }).id;
    if (!mediaId) throw new ProviderError("WhatsApp media upload returned no id.", true);

    // 2. Send the approved template: image header + the five body variables, in template order.
    const f = input.facts;
    const send = await post(`${this.base}/messages`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        messaging_product: "whatsapp",
        to: input.to.replace(/^\+/, ""),
        type: "template",
        template: {
          name: templateName,
          language: { code: templateLanguage },
          components: [
            { type: "header", parameters: [{ type: "image", image: { id: mediaId } }] },
            { type: "body", parameters: [f.participantName, f.eventTitle, f.dateText, f.timeText, f.location].map((text) => ({ type: "text", text })) },
          ],
        },
      }),
    });
    const sendText = await send.text();
    if (!send.ok) throw failFor(send.status, sendText);
    const id = (JSON.parse(sendText) as { messages?: { id?: string }[] }).messages?.[0]?.id;
    if (!id) throw new ProviderError("WhatsApp returned no message id.", true);
    return { providerMessageId: id };
  }
}
