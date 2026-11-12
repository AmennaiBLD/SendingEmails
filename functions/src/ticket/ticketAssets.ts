import PDFDocument from "pdfkit";
import QRCode from "qrcode";
import sharp from "sharp";

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
const HEIGHT = 1680;
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

/** Initials for the placeholder logo mark. Replace with the real logo once the club supplies one. */
function monogram(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join("") || "SC";
}

/** Plain, undecorated QR: black on white with a full quiet zone, so any scanner reads it. */
export function renderQrPng(payload: string, size = QR_SIZE): Promise<Buffer> {
  return QRCode.toBuffer(payload, { errorCorrectionLevel: "M", margin: 4, width: size, color: { dark: "#000000", light: "#ffffff" } });
}

function ticketSvg(info: TicketInfo): string {
  const title = wrap(info.eventTitle, 30, 2);
  const titleSvg = title.map((l, i) => `<text x="80" y="${330 + i * 68}" class="title">${esc(l)}</text>`).join("");
  const detailsY = 330 + title.length * 68 + 30;
  const row = (y: number, label: string, value: string) =>
    `<text x="80" y="${y}" class="label">${label}</text><text x="80" y="${y + 44}" class="value">${esc(wrap(value, 46, 1)[0] ?? "")}</text>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#27357E"/><stop offset="1" stop-color="#0B7A75"/></linearGradient></defs>
  <style>
    text { font-family: 'Helvetica Neue', Helvetica, Arial, 'DejaVu Sans', 'Noto Sans', sans-serif; }
    .club { font-size: 34px; font-weight: 700; fill: #ffffff; }
    .kicker { font-size: 24px; letter-spacing: 6px; fill: #BFE9EC; }
    .title { font-size: 56px; font-weight: 700; fill: #0F172A; }
    .label { font-size: 22px; letter-spacing: 3px; fill: #64748B; }
    .value { font-size: 34px; font-weight: 600; fill: #0F172A; }
    .status { font-size: 26px; font-weight: 700; letter-spacing: 3px; fill: #0B6B43; }
    .hint { font-size: 24px; fill: #475569; }
  </style>
  <rect width="${WIDTH}" height="${HEIGHT}" fill="#EEF2F7"/>
  <rect x="40" y="40" width="${WIDTH - 80}" height="${HEIGHT - 80}" rx="44" fill="#ffffff"/>
  <path d="M40 84 a44 44 0 0 1 44 -44 h${WIDTH - 168} a44 44 0 0 1 44 44 v136 h-${WIDTH - 80} z" fill="url(#g)"/>
  <circle cx="132" cy="130" r="46" fill="#ffffff" fill-opacity="0.16"/>
  <text x="132" y="143" text-anchor="middle" class="club">${esc(monogram(info.clubName))}</text>
  <text x="204" y="118" class="kicker">EVENT TICKET</text>
  <text x="204" y="162" class="club">${esc(wrap(info.clubName, 36, 1)[0] ?? "")}</text>
  ${titleSvg}
  ${row(detailsY, "PARTICIPANT", info.participantName)}
  ${row(detailsY + 110, "DATE", info.dateText)}
  ${row(detailsY + 220, "TIME", info.timeText)}
  ${row(detailsY + 330, "LOCATION", info.location)}
  <line x1="80" y1="${detailsY + 420}" x2="${WIDTH - 80}" y2="${detailsY + 420}" stroke="#CBD5E1" stroke-width="3" stroke-dasharray="14 12"/>
  <rect x="${(WIDTH - 330) / 2}" y="${HEIGHT - 170}" width="330" height="56" rx="28" fill="#DCFCE7"/>
  <text x="${WIDTH / 2}" y="${HEIGHT - 132}" text-anchor="middle" class="status">✓ CONFIRMED</text>
  <text x="${WIDTH / 2}" y="${HEIGHT - 78}" text-anchor="middle" class="hint">Show this QR code when you arrive and when you leave.</text>
</svg>`;
}

/** The designed ticket: details rendered around an untouched QR code. */
export async function renderTicketPng(info: TicketInfo, qrPng: Buffer): Promise<Buffer> {
  const qr = await sharp(qrPng).resize(QR_SIZE, QR_SIZE, { kernel: "nearest" }).png().toBuffer();
  return sharp(Buffer.from(ticketSvg(info)))
    .composite([{ input: qr, left: Math.round((WIDTH - QR_SIZE) / 2), top: HEIGHT - 190 - QR_SIZE - 20 }])
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
