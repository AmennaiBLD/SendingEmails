/**
 * Email templates in the GDG On Campus USTO-MB design (design/emailDesign EML01 600 px desktop,
 * EML02 375 px phone): lavender page, the GDG lockup (inline `cid:gdg-lockup`), a light card with
 * the primary-container hero, event facts, the QR block on white, the two steps, the four-colour
 * Google bar and the footer. Table layout with inline styles only (Gmail, Outlook, Apple Mail);
 * one media query narrows paddings and type on phones. Font stack 'Google Sans', Roboto, Arial.
 * Each template returns a subject, the HTML and a plain-text alternative.
 *
 * Owner rule (beats EML01's pending state): the "registration received" email carries NO QR code.
 * The QR is only sent once an administrator approves the registration.
 */
import { LOCKUP_CID } from "../shared/brand";

export interface EmailContent {
  subject: string;
  html: string;
  text: string;
}

export interface EventFacts {
  clubName: string;
  participantName: string;
  eventTitle: string;
  dateText: string;
  timeText: string;
  location: string;
  /** Public site root (no trailing slash). When set, emails link to "Find my ticket". */
  siteUrl?: string;
}

// design-system/tokens.json → color.light (m3 participant palette) and brand colours.
const PAGE = "#eeedf4"; // m3 surface-container-high
const CARD = "#faf8ff"; // m3 surface
const STEP_BG = "#f4f3fa"; // m3 surface-container-low
const INK = "#1a1b21"; // m3 on-surface
const MUTED = "#44464f"; // m3 on-surface-variant
const OUTLINE_V = "#c5c6d0"; // m3 outline-variant
const PRIMARY = "#485e92"; // m3 primary
const HERO_BG = "#adc3fe"; // m3 primary-container
const HERO_INK = "#324574"; // m3 on-primary-container
const WARN_BG = "#fef3e0";
const WARN_INK = "#8a4b00";
const OK_BG = "#e8f5ec";
const OK_INK = "#1b6b35";
const GOOGLE = ["#4285f4", "#ea4335", "#fbbc05", "#34a853"];

const FONT = "'Google Sans',Roboto,Arial,Helvetica,sans-serif";

function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? name;
}

interface Hero {
  overline: string;
  title: string;
  /** Already-escaped HTML. */
  greeting: string;
}

/** The whole message: lockup, card with the hero and the blocks, Google bar, footer. */
function layout(clubName: string, preheader: string, hero: Hero, blocks: string): string {
  const bar = GOOGLE.map((c, i) =>
    `<td width="40" height="4" style="width:40px;height:4px;background:${c};border-radius:2px;font-size:0;line-height:0;">&nbsp;</td>${i < 3 ? `<td width="6" style="width:6px;font-size:0;">&nbsp;</td>` : ""}`,
  ).join("");
  return `<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="supported-color-schemes" content="light">
<title>${esc(hero.title)}</title>
<style>
  @media (max-width: 620px) {
    .outer { padding: 20px 10px !important; }
    .px { padding-left: 24px !important; padding-right: 24px !important; }
    .logo { padding: 4px 24px 18px 24px !important; }
    .logo img { width: 184px !important; height: 24px !important; }
    .hero { padding: 28px 24px 24px 24px !important; }
    .hero-title { font-size: 26px !important; line-height: 32px !important; }
    .hero-text { font-size: 15px !important; line-height: 22px !important; }
    .stack { display: block !important; width: 100% !important; padding-left: 0 !important; padding-right: 0 !important; }
    .card { border-radius: 24px !important; }
    .qr img { width: 200px !important; height: 200px !important; }
  }
</style>
</head>
<body style="margin:0;padding:0;background:${PAGE};-webkit-text-size-adjust:100%;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:${PAGE};">${esc(preheader)}&#8203;&nbsp;&#8203;&nbsp;&#8203;&nbsp;</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="${PAGE}" style="background:${PAGE};">
<tr><td class="outer" align="center" style="padding:40px 12px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;border-collapse:collapse;font-family:${FONT};color:${INK};">

<tr><td class="logo" style="padding:8px 32px 24px 32px;">
  <img src="cid:${LOCKUP_CID}" width="245" height="32" alt="Google Developer Group · On Campus · USTO-MB" style="display:block;border:0;width:245px;height:32px;">
</td></tr>

<tr><td class="card" bgcolor="${CARD}" style="background:${CARD};border-radius:28px;overflow:hidden;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">
    <tr><td class="hero" bgcolor="${HERO_BG}" style="background:${HERO_BG};color:${HERO_INK};padding:36px 40px 32px 40px;border-radius:28px 28px 0 0;">
      <p style="margin:0;font-size:12px;line-height:16px;letter-spacing:1.5px;text-transform:uppercase;font-weight:bold;color:${HERO_INK};">${esc(hero.overline)}</p>
      <h1 class="hero-title" style="margin:12px 0 0 0;font-size:32px;line-height:40px;font-weight:bold;color:${HERO_INK};">${esc(hero.title)}</h1>
      <p class="hero-text" style="margin:16px 0 0 0;font-size:16px;line-height:24px;color:${HERO_INK};">${hero.greeting}</p>
    </td></tr>
    ${blocks}
  </table>
</td></tr>

<tr><td align="center" style="padding:28px 40px 8px 40px;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;"><tr>${bar}</tr></table>
</td></tr>
<tr><td class="px" align="center" style="padding:12px 48px 0 48px;font-size:13px;line-height:20px;color:${MUTED};">
  You receive this email because you registered for an event of ${esc(clubName)}.<br>Questions? Simply reply to this email.
</td></tr>

</table>
</td></tr>
</table>
</body>
</html>`;
}

/** A warning (or success) banner at the top of the card. */
function banner(text: string, fg = WARN_INK, bg = WARN_BG): string {
  return `<tr><td class="px" style="padding:28px 40px 0 40px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;"><tr>
      <td bgcolor="${bg}" style="background:${bg};color:${fg};border-radius:16px;padding:18px 20px;font-size:15px;line-height:22px;">${text}</td>
    </tr></table>
  </td></tr>`;
}

/** Date, time, place and participant: a 2 × 2 grid, stacked on phones. */
function factsBlock(f: EventFacts): string {
  const cell = (label: string, value: string, left: boolean) =>
    `<td class="stack" width="50%" valign="top" style="padding:${left ? "0 12px 20px 0" : "0 0 20px 12px"};">
      <p style="margin:0;font-size:11px;line-height:16px;letter-spacing:1px;text-transform:uppercase;color:${MUTED};">${esc(label)}</p>
      <p style="margin:4px 0 0 0;font-size:16px;line-height:22px;font-weight:bold;color:${INK};">${esc(value)}</p>
    </td>`;
  return `<tr><td class="px" style="padding:28px 40px 8px 40px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">
      <tr>${cell("Date", f.dateText, true)}${cell("Time", f.timeText, false)}</tr>
      <tr>${cell("Location", f.location, true)}${cell("Participant", f.participantName, false)}</tr>
    </table>
  </td></tr>`;
}

/** The dashed tear line of a paper ticket. */
const PERFORATION = `<tr><td class="px" style="padding:0 40px;"><div style="border-top:2px dashed ${OUTLINE_V};height:0;line-height:0;font-size:0;">&nbsp;</div></td></tr>`;

/** Status pill, the QR (`cid:ticket-qr`, always on white) and "Show this at the door". */
function qrBlock(pill: string): string {
  return `<tr><td align="center" class="px" style="padding:32px 40px 8px 40px;">
      <span style="display:inline-block;background:${OK_BG};color:${OK_INK};border-radius:8px;padding:6px 14px;font-size:12px;line-height:16px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;">${esc(pill)}</span>
    </td></tr>
    <tr><td align="center" class="px qr" style="padding:16px 40px 0 40px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;"><tr>
        <td bgcolor="#ffffff" style="background:#ffffff;border:1px solid ${OUTLINE_V};border-radius:20px;padding:16px;">
          <img src="cid:ticket-qr" width="220" height="220" alt="Your QR ticket" style="display:block;border:0;width:220px;height:220px;">
        </td>
      </tr></table>
    </td></tr>
    <tr><td align="center" class="px" style="padding:16px 40px 28px 40px;">
      <p style="margin:0;font-size:13px;line-height:18px;letter-spacing:2px;text-transform:uppercase;color:${PRIMARY};font-weight:bold;">Show this at the door</p>
    </td></tr>`;
}

/** Arrival and departure: the same QR is scanned twice. */
function stepsBlock(): string {
  const step = (n: string, title: string, body: string) => `<tr><td bgcolor="${STEP_BG}" style="background:${STEP_BG};border-radius:16px;padding:18px 20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;"><tr>
        <td valign="top" width="40"><div style="width:28px;height:28px;border-radius:14px;background:${PRIMARY};color:#ffffff;font-size:13px;line-height:28px;text-align:center;font-weight:bold;">${n}</div></td>
        <td valign="top"><p style="margin:0;font-size:15px;line-height:22px;font-weight:bold;color:${INK};">${esc(title)}</p><p style="margin:2px 0 0 0;font-size:14px;line-height:21px;color:${MUTED};">${esc(body)}</p></td>
      </tr></table>
    </td></tr>`;
  return `<tr><td class="px" style="padding:0 40px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:separate;border-spacing:0 10px;">
      ${step("1", "Arrival", "Show the QR code at the entrance. An organizer scans it to check you in.")}
      ${step("2", "Departure", "Show the same QR code again when you leave, to check out.")}
    </table>
  </td></tr>`;
}

function noteBlock(html: string): string {
  return `<tr><td class="px" style="padding:18px 40px 0 40px;font-size:14px;line-height:22px;color:${MUTED};">${html}</td></tr>`;
}

/** "Find my ticket online" (filled primary pill), only when the public site address is known. */
function findTicketButton(f: EventFacts): string {
  if (!f.siteUrl) return `<tr><td style="padding:0 0 36px 0;font-size:0;line-height:0;">&nbsp;</td></tr>`;
  // The web app has no per-page URLs: the home page leads to "Find my ticket".
  const href = f.siteUrl.replace(/\/+$/, "");
  return `<tr><td class="px" style="padding:24px 40px 36px 40px;">
    <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;"><tr>
      <td bgcolor="${PRIMARY}" style="background:${PRIMARY};border-radius:20px;"><a href="${esc(href)}" style="display:inline-block;padding:10px 24px;font-size:14px;line-height:20px;font-weight:bold;color:#ffffff;text-decoration:none;">Find my ticket online</a></td>
    </tr></table>
  </td></tr>`;
}

function factsText(f: EventFacts): string {
  return `Event: ${f.eventTitle}\nDate: ${f.dateText}\nTime: ${f.timeText}\nLocation: ${f.location}`;
}

const ATTACHMENTS_NOTE =
  "Your ticket is also attached as an image and as a PDF, so you can open it without internet at the door. " +
  `<strong style="color:${INK};">Keep it private:</strong> anyone holding this QR code can use it.`;

/** The QR ticket email body shared by confirmation, approval and replacement. */
function ticketBlocks(f: EventFacts, pill: string, warning?: string): string {
  return (warning ? banner(warning) : "") + factsBlock(f) + PERFORATION + qrBlock(pill) + stepsBlock() + noteBlock(ATTACHMENTS_NOTE) + findTicketButton(f);
}

export function registrationConfirmationEmail(f: EventFacts): EmailContent {
  return {
    subject: `You're in: ${f.eventTitle} 🎉`,
    html: layout(
      f.clubName,
      `Your QR ticket for ${f.eventTitle}, ${f.dateText}.`,
      { overline: "You’re on the list", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, your registration is confirmed. See you at the event!` },
      ticketBlocks(f, "Confirmed"),
    ),
    text: `Hello ${f.participantName},\n\nYour registration is confirmed.\n\n${factsText(f)}\n\nYour QR ticket is attached (image and PDF). Show it at the entrance, and again when you leave.\nKeep it private: anyone holding the QR code can use it.\n\n${f.clubName}`,
  };
}

/** Sent right after registering: the registration waits for approval. No QR: it comes with the approval. */
export function registrationReceivedEmail(f: EventFacts): EmailContent {
  return {
    subject: `Registration received: ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `We received your registration for ${f.eventTitle}. Please wait for the approval.`,
      { overline: "Registration received", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, thank you for registering!` },
      banner("Your registration is waiting for approval. As soon as it is approved, we will email you your QR ticket.") +
        factsBlock(f) +
        noteBlock("There is nothing else to do for now. Keep an eye on your inbox (and your spam folder) for the email with your QR code.") +
        findTicketButton(f),
    ),
    text: `Hello ${f.participantName},\n\nThank you for registering. Your registration is waiting for approval.\nAs soon as it is approved, we will email you your QR ticket.\n\n${factsText(f)}\n\n${f.clubName}`,
  };
}

/** Sent when an administrator does not approve an application: a clear, kind answer, no QR. */
export function registrationRejectedEmail(f: EventFacts): EmailContent {
  return {
    subject: `Your registration for ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `About your registration for ${f.eventTitle}.`,
      { overline: "Registration not approved", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, thank you for your interest in this event.` },
      banner("We are sorry: your registration could not be approved this time, so there is no ticket for this event. Places are limited and every request was reviewed carefully.") +
        factsBlock(f) +
        noteBlock("Please do not come to the entrance for this event. We hope to see you at one of our next events: keep an eye on our website and social media.") +
        findTicketButton(f),
    ),
    text: `Hello ${f.participantName},\n\nThank you for your interest. We are sorry: your registration for ${f.eventTitle} could not be approved this time, so there is no ticket for this event.\n\n${factsText(f)}\n\nWe hope to see you at one of our next events.\n\n${f.clubName}`,
  };
}

/** Sent when an administrator approves: the QR ticket, valid from now on. */
export function registrationApprovedEmail(f: EventFacts): EmailContent {
  return {
    subject: `You're approved: ${f.eventTitle} 🎉`,
    html: layout(
      f.clubName,
      `Your place at ${f.eventTitle} is confirmed. Here is your QR ticket.`,
      { overline: "You’re on the list", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, your registration is approved. See you at the event!` },
      ticketBlocks(f, "Confirmed"),
    ),
    text: `Hello ${f.participantName},\n\nYour registration is approved. Your QR ticket is attached (image and PDF).\n\n${factsText(f)}\n\nShow it at the entrance, and again when you leave.\nKeep it private: anyone holding the QR code can use it.\n\n${f.clubName}`,
  };
}

export function ticketReplacementEmail(f: EventFacts): EmailContent {
  return {
    subject: `Your new ticket for ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `Your new QR ticket for ${f.eventTitle}. The previous one no longer works.`,
      { overline: "New ticket", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, here is your replacement ticket.` },
      ticketBlocks(f, "Valid ticket", "Your previous QR code no longer works. Use only the one in this email."),
    ),
    text: `Hello ${f.participantName},\n\nHere is your replacement ticket. Any earlier ticket no longer works.\n\n${factsText(f)}\n\n${f.clubName}`,
  };
}

export function ticketAccessCodeEmail(clubName: string, eventTitle: string, code: string): EmailContent {
  const digits = code.split("").map((d) =>
    `<td width="44" height="56" align="center" valign="middle" bgcolor="#ffffff" style="width:44px;height:56px;border:1px solid ${OUTLINE_V};border-radius:12px;background:#ffffff;font-size:28px;font-weight:bold;color:${INK};">${esc(d)}</td><td width="8" style="width:8px;font-size:0;">&nbsp;</td>`,
  ).join("");
  return {
    subject: `${code} is your ticket access code`,
    html: layout(
      clubName,
      `Your code to open your ticket: ${code}`,
      { overline: "Ticket access", title: "Your access code", greeting: `Use it to open your ticket for <strong>${esc(eventTitle)}</strong>.` },
      `<tr><td align="center" style="padding:32px 20px 6px;"><table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>${digits}</tr></table></td></tr>` +
        noteBlock("The code expires in 15 minutes. If you did not ask for it, you can ignore this email: your ticket stays safe.") +
        `<tr><td style="padding:0 0 36px 0;font-size:0;line-height:0;">&nbsp;</td></tr>`,
    ),
    text: `Your ticket access code for ${eventTitle} is ${code}. It expires in 15 minutes.`,
  };
}

export function eventReminderEmail(f: EventFacts): EmailContent {
  return {
    subject: `Reminder: ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `${f.eventTitle} is coming up: ${f.dateText}, ${f.timeText}.`,
      { overline: "See you soon", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, this is a friendly reminder for your event.` },
      factsBlock(f) + stepsBlock() + noteBlock("Bring the QR ticket from your confirmation email, on your phone or printed.") + findTicketButton(f),
    ),
    text: `Hello ${f.participantName},\n\nReminder for your upcoming event.\n\n${factsText(f)}\n\n${f.clubName}`,
  };
}

export function eventCancellationEmail(f: EventFacts): EmailContent {
  return {
    subject: `Cancelled: ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `${f.eventTitle} has been cancelled.`,
      { overline: "Event cancelled", title: f.eventTitle, greeting: `Hello ${esc(firstName(f.participantName))}, we are sorry: this event has been cancelled.` },
      banner("Your ticket for this event is no longer valid.") + factsBlock(f) + noteBlock("We hope to see you at one of our next events.") +
        `<tr><td style="padding:0 0 36px 0;font-size:0;line-height:0;">&nbsp;</td></tr>`,
    ),
    text: `Hello ${f.participantName},\n\n${f.eventTitle} has been cancelled. Your ticket is no longer valid.\n\n${f.clubName}`,
  };
}
