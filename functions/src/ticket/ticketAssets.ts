import PDFDocument from "pdfkit";
import QRCode from "qrcode";
import sharp from "sharp";
import { lockupPng } from "../shared/brand";

export interface TicketInfo {
  clubName: string;
  eventTitle: string;
  participantName: string;
  dateText: string;
  timeText: string;
  location: string;
}

export interface TicketAssets {
  qrPng: Buffer;
  ticketPng: Buffer;
  ticketPdf: Buffer;
}

const WIDTH = 1080;
const HEIGHT = 1920; // room for a two-line title, four facts and the QR without overlap
const QR_SIZE = 640;

function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function wrap(text: string, perLine: number, maxLines: number): string[] {
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(/\s+/)) {
    if ((line + " " + word).trim().length > perLine && line) {
      lines.push(line);
      line = word;
    } else {
      line = (line + " " + word).trim();
    }
  }
  if (line) lines.push(line);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines);
    kept[maxLines - 1] = kept[maxLines - 1].slice(0, perLine - 1) + "…";
    return kept;
  }
  return lines;
}

/** Plain, undecorated QR: black on white with a full quiet zone, so any scanner reads it. */
export function renderQrPng(payload: string, size = QR_SIZE): Promise<Buffer> {
  return QRCode.toBuffer(payload, { errorCorrectionLevel: "M", margin: 4, width: size, color: { dark: "#000000", light: "#ffffff" } });
}

/**
 * The ticket image in the GDG design (EML01 / PUB04 colours): white card on the lavender page,
 * primary-container header with the lockup composited on top, facts, dashed tear line, then the QR.
 */
function ticketSvg(info: TicketInfo): string {
  const title = wrap(info.eventTitle, 30, 2);
  const titleSvg = title.map((l, i) => `<text x="80" y="${360 + i * 68}" class="title">${esc(l)}</text>`).join("");
  const detailsY = 360 + title.length * 68 + 30;
  const row = (y: number, label: string, value: string) =>
    `<text x="80" y="${y}" class="label">${label}</text><text x="80" y="${y + 44}" class="value">${esc(wrap(value, 46, 1)[0] ?? "")}</text>`;
  const bar = ["#4285f4", "#ea4335", "#fbbc05", "#34a853"].map((c, i) => `<rect x="${80 + i * 92}" y="232" width="80" height="8" rx="4" fill="${c}"/>`).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">
  <style>
    text { font-family: 'Google Sans', Roboto, 'Helvetica Neue', Helvetica, Arial, 'DejaVu Sans', 'Noto Sans', sans-serif; }
    .kicker { font-size: 24px; font-weight: 700; letter-spacing: 4px; fill: #324574; }
    .title { font-size: 56px; font-weight: 700; fill: #1a1b21; }
    .label { font-size: 22px; letter-spacing: 3px; fill: #44464f; }
    .value { font-size: 34px; font-weight: 700; fill: #1a1b21; }
    .status { font-size: 26px; font-weight: 700; letter-spacing: 3px; fill: #1b6b35; }
    .hint { font-size: 24px; fill: #44464f; }
  </style>
  <rect width="${WIDTH}" height="${HEIGHT}" fill="#eeedf4"/>
  <rect x="40" y="40" width="${WIDTH - 80}" height="${HEIGHT - 80}" rx="56" fill="#faf8ff"/>
  <path d="M40 96 a56 56 0 0 1 56 -56 h${WIDTH - 192} a56 56 0 0 1 56 56 v164 h-${WIDTH - 80} z" fill="#adc3fe"/>
  <text x="80" y="200" class="kicker">EVENT TICKET</text>
  ${bar}
  ${titleSvg}
  ${row(detailsY, "PARTICIPANT", info.participantName)}
  ${row(detailsY + 110, "DATE", info.dateText)}
  ${row(detailsY + 220, "TIME", info.timeText)}
  ${row(detailsY + 330, "LOCATION", info.location)}
  <line x1="80" y1="${detailsY + 420}" x2="${WIDTH - 80}" y2="${detailsY + 420}" stroke="#c5c6d0" stroke-width="3" stroke-dasharray="14 12"/>
  <rect x="${(WIDTH - 330) / 2}" y="${HEIGHT - 170}" width="330" height="56" rx="16" fill="#e8f5ec"/>
  <text x="${WIDTH / 2}" y="${HEIGHT - 132}" text-anchor="middle" class="status">CONFIRMED</text>
  <text x="${WIDTH / 2}" y="${HEIGHT - 78}" text-anchor="middle" class="hint">Show this QR code when you arrive and when you leave.</text>
</svg>`;
}

/** The designed ticket: details rendered around an untouched QR code. */
export async function renderTicketPng(info: TicketInfo, qrPng: Buffer): Promise<Buffer> {
  const qr = await sharp(qrPng).resize(QR_SIZE, QR_SIZE, { kernel: "nearest" }).png().toBuffer();
  const layers: { input: Buffer; left: number; top: number }[] = [{ input: qr, left: Math.round((WIDTH - QR_SIZE) / 2), top: HEIGHT - 190 - QR_SIZE - 20 }];
  // The supplied lockup (never a text stand-in), 64 px tall in the header.
  const logo = lockupPng();
  if (logo) layers.push({ input: await sharp(logo).resize({ height: 64 }).png().toBuffer(), left: 80, top: 84 });
  return sharp(Buffer.from(ticketSvg(info)))
    .composite(layers)
    .png()
    .toBuffer();
}

export function renderTicketPdf(ticketPng: Buffer): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: [WIDTH / 2, HEIGHT / 2], margin: 0 });
    const chunks: Buffer[] = [];
    doc.on("data", (c: Buffer) => chunks.push(c));
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.on("error", reject);
    doc.image(ticketPng, 0, 0, { width: WIDTH / 2, height: HEIGHT / 2 });
    doc.end();
  });
}

export async function renderTicketAssets(info: TicketInfo, payload: string): Promise<TicketAssets> {
  const qrPng = await renderQrPng(payload);
  const ticketPng = await renderTicketPng(info, qrPng);
  const ticketPdf = await renderTicketPdf(ticketPng);
  return { qrPng, ticketPng, ticketPdf };
}
