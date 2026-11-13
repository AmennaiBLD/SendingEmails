/**
 * Email templates in the app's look: dark navy page, the navy-to-teal ticket header, BBH Bogle
 * headings (Apple Mail / iOS load it; Gmail and Outlook fall back to a bold sans-serif).
 * Table layout with inline styles only, so Gmail, Outlook and Apple Mail render the same.
 * Each template returns a subject, the HTML and a plain-text alternative.
 */
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
}

// The app's colours (shared/.../ui/theme/Theme.kt and TicketCard.kt).
const PAGE = "#0B1020";
const CARD = "#151B2E";
const LINE = "#26304A";
const TEXT = "#F2F5FB";
const MUTED = "#93A2C3";
const ACCENT = "#B4C0FF";
const NAVY = "#27357E";
const TEAL = "#0B7A75";
const SUCCESS_BG = "#16432F";
const SUCCESS = "#7BE0A6";
const WARNING_BG = "#4A3510";
const WARNING = "#FFD48A";

const DISPLAY = "'BBH Bogle','Arial Black','Helvetica Neue',Helvetica,Arial,sans-serif";
const BODY = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function monogram(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join("") || "SC";
}

function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? name;
}

interface Hero {
  kicker: string;
  title: string;
  subtitle: string;
}

/** The whole message: club bar, gradient header, the content blocks, footer. */
function layout(clubName: string, preheader: string, hero: Hero, blocks: string): string {
  return `<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark light">
<meta name="supported-color-schemes" content="dark light">
<title>${esc(hero.title)}</title>
<link href="https://fonts.googleapis.com/css2?family=BBH+Bogle&display=swap" rel="stylesheet">
<style>
  @media (max-width: 620px) {
    .px { padding-left: 20px !important; padding-right: 20px !important; }
    .stack { display: block !important; width: 100% !important; }
    .stack-gap { padding-top: 12px !important; padding-left: 0 !important; }
    .hero-title { font-size: 26px !important; line-height: 32px !important; }
  }
</style>
</head>
<body style="margin:0;padding:0;background:${PAGE};-webkit-text-size-adjust:100%;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:${PAGE};">${esc(preheader)}&#8203;&nbsp;&#8203;&nbsp;&#8203;&nbsp;</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="${PAGE}" style="background:${PAGE};">
<tr><td align="center" style="padding:28px 12px 40px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px;">

<tr><td class="px" style="padding:0 8px 18px;">
  <table role="presentation" cellpadding="0" cellspacing="0"><tr>
    <td width="40" height="40" align="center" valign="middle" bgcolor="${TEAL}" style="width:40px;height:40px;border-radius:12px;background:${TEAL};background-image:linear-gradient(135deg,#A9C3FF,#7FD6CF);font-family:${DISPLAY};font-size:15px;color:#0B1020;">${esc(monogram(clubName))}</td>
    <td style="padding-left:12px;font-family:${DISPLAY};font-size:16px;letter-spacing:1px;color:${TEXT};">${esc(clubName.toUpperCase())}</td>
  </tr></table>
</td></tr>

<tr><td>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="${CARD}" style="background:${CARD};border-radius:22px;overflow:hidden;border:1px solid ${LINE};">
    <tr><td class="px" bgcolor="${NAVY}" style="background:${NAVY};background-image:linear-gradient(135deg,${NAVY} 0%,${TEAL} 100%);padding:30px 32px 32px;">
      <div style="font-family:${DISPLAY};font-size:12px;letter-spacing:3px;color:#BFE9EC;">${esc(hero.kicker.toUpperCase())}</div>
      <div class="hero-title" style="font-family:${DISPLAY};font-size:32px;line-height:38px;color:#FFFFFF;margin-top:10px;">${esc(hero.title)}</div>
      <div style="font-family:${BODY};font-size:15px;line-height:22px;color:#DCE6FF;margin-top:10px;">${hero.subtitle}</div>
    </td></tr>
    ${blocks}
  </table>
</td></tr>

<tr><td class="px" align="center" style="padding:24px 24px 0;font-family:${BODY};font-size:12px;line-height:18px;color:${MUTED};">
  You receive this email because you registered for an event of ${esc(clubName)}.<br>
  Questions? Simply reply to this email.
</td></tr>

</table>
</td></tr>
</table>
</body>
</html>`;
}

function label(text: string): string {
  return `<div style="font-family:${DISPLAY};font-size:11px;letter-spacing:2px;color:${MUTED};">${esc(text.toUpperCase())}</div>`;
}

function value(text: string): string {
  return `<div style="font-family:${BODY};font-size:16px;line-height:22px;font-weight:600;color:${TEXT};margin-top:4px;">${esc(text)}</div>`;
}

/** Date, time, place and participant in a 2 × 2 grid (stacked on phones). */
function detailsBlock(f: EventFacts): string {
  const cell = (l: string, v: string, first: boolean) =>
    `<td class="stack${first ? "" : " stack-gap"}" width="50%" valign="top" style="${first ? "" : "padding-left:16px;"}">${label(l)}${value(v)}</td>`;
  return `<tr><td class="px" style="padding:26px 32px 6px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
      <tr>${cell("Date", f.dateText, true)}${cell("Time", f.timeText, false)}</tr>
      <tr><td colspan="2" style="height:18px;line-height:18px;font-size:0;">&nbsp;</td></tr>
      <tr>${cell("Location", f.location, true)}${cell("Participant", f.participantName, false)}</tr>
    </table>
  </td></tr>`;
}

/** The dashed tear line of a paper ticket. */
const PERFORATION = `<tr><td style="padding:22px 0 0;"><div style="border-top:2px dashed ${LINE};height:0;line-height:0;font-size:0;">&nbsp;</div></td></tr>`;

function chip(text: string, fg: string, bg: string): string {
  return `<span style="display:inline-block;padding:6px 14px;border-radius:999px;background:${bg};font-family:${DISPLAY};font-size:11px;letter-spacing:2px;color:${fg};">${esc(text.toUpperCase())}</span>`;
}

/** `cid:ticket-qr` is the inline QR attachment the email provider adds. Always on white. */
function qrBlock(status: string, pending = false): string {
  return `<tr><td class="px" align="center" style="padding:26px 32px 8px;">
    ${pending ? chip(status, WARNING, WARNING_BG) : chip(status, SUCCESS, SUCCESS_BG)}
    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:18px auto 0;"><tr>
      <td bgcolor="#FFFFFF" style="background:#FFFFFF;border-radius:18px;padding:14px;">
        <img src="cid:ticket-qr" width="232" height="232" alt="Your QR ticket" style="display:block;width:232px;height:232px;border:0;outline:none;">
      </td>
    </tr></table>
    <div style="font-family:${DISPLAY};font-size:13px;letter-spacing:2px;color:${ACCENT};margin-top:16px;">SHOW THIS AT THE DOOR</div>
  </td></tr>`;
}

/** Arrival and departure: the same QR is scanned twice. */
function stepsBlock(): string {
  const step = (n: string, title: string, body: string, first: boolean) => `<td class="stack${first ? "" : " stack-gap"}" width="50%" valign="top" style="${first ? "" : "padding-left:12px;"}">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="${PAGE}" style="background:${PAGE};border:1px solid ${LINE};border-radius:14px;"><tr><td style="padding:16px;">
        <table role="presentation" cellpadding="0" cellspacing="0"><tr>
          <td width="28" height="28" align="center" valign="middle" bgcolor="${NAVY}" style="width:28px;height:28px;border-radius:14px;background:${NAVY};font-family:${DISPLAY};font-size:13px;color:#FFFFFF;">${n}</td>
          <td style="padding-left:10px;font-family:${DISPLAY};font-size:13px;letter-spacing:1px;color:${TEXT};">${esc(title.toUpperCase())}</td>
        </tr></table>
        <div style="font-family:${BODY};font-size:14px;line-height:20px;color:${MUTED};margin-top:8px;">${body}</div>
      </td></tr></table>
    </td>`;
  return `<tr><td class="px" style="padding:22px 32px 4px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
      ${step("1", "Arrival", "Show the QR code at the entrance. An organizer scans it to check you in.", true)}
      ${step("2", "Departure", "Show the same QR code again when you leave, to check out.", false)}
    </tr></table>
  </td></tr>`;
}

function noteBlock(html: string, last = true): string {
  return `<tr><td class="px" style="padding:20px 32px ${last ? "30px" : "0"};font-family:${BODY};font-size:13px;line-height:20px;color:${MUTED};">${html}</td></tr>`;
}

function banner(text: string, fg: string, bg: string): string {
  return `<tr><td class="px" style="padding:24px 32px 0;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
      <td bgcolor="${bg}" style="background:${bg};border-radius:12px;padding:14px 16px;font-family:${BODY};font-size:14px;line-height:20px;font-weight:600;color:${fg};">${text}</td>
    </tr></table>
  </td></tr>`;
}

function factsText(f: EventFacts): string {
  return `Event: ${f.eventTitle}\nDate: ${f.dateText}\nTime: ${f.timeText}\nLocation: ${f.location}`;
}

const ATTACHMENTS_NOTE =
  "Your ticket is also attached as an image and as a PDF, so you can open it without internet at the door. " +
  "<strong style=\"color:#F2F5FB;\">Keep it private:</strong> anyone holding this QR code can use it.";

export function registrationConfirmationEmail(f: EventFacts): EmailContent {
  return {
    subject: `You're in: ${f.eventTitle} 🎉`,
    html: layout(
      f.clubName,
      `Your QR ticket for ${f.eventTitle}, ${f.dateText}.`,
      { kicker: "Ticket confirmed", title: f.eventTitle, subtitle: `Hello ${esc(firstName(f.participantName))}, your seat is saved. We can't wait to see you!` },
      detailsBlock(f) + PERFORATION + qrBlock("Confirmed") + stepsBlock() + noteBlock(ATTACHMENTS_NOTE),
    ),
    text: `Hello ${f.participantName},\n\nYour registration is confirmed.\n\n${factsText(f)}\n\nYour QR ticket is attached (image and PDF). Show it at the entrance, and again when you leave.\nKeep it private: anyone holding the QR code can use it.\n\n${f.clubName}`,
  };
}

/** Sent right after registering, while the registration waits for approval: the same QR as on screen. */
export function registrationReceivedEmail(f: EventFacts): EmailContent {
  return {
    subject: `Registration received: ${f.eventTitle} 🎟️`,
    html: layout(
      f.clubName,
      `Thanks for registering for ${f.eventTitle}. Here is your QR ticket.`,
      { kicker: "Registration received", title: f.eventTitle, subtitle: `Hello ${esc(firstName(f.participantName))}, thank you for registering! Here is your personal QR ticket.` },
      banner("Your registration is being reviewed. This QR code works at the entrance once it is approved, and we will email you as soon as it is.", WARNING, WARNING_BG) +
        detailsBlock(f) + PERFORATION + qrBlock("Pending approval", true) + stepsBlock() + noteBlock(ATTACHMENTS_NOTE),
    ),
    text: `Hello ${f.participantName},\n\nThank you for registering. Your QR ticket is attached (image and PDF).\nYour registration is being reviewed: the QR code works at the entrance once it is approved, and we will email you then.\n\n${factsText(f)}\n\n${f.clubName}`,
  };
}

/** Sent when an administrator approves: the QR the participant already has is now valid. */
export function registrationApprovedEmail(f: EventFacts): EmailContent {
  return {
    subject: `You're approved: ${f.eventTitle} 🎉`,
    html: layout(
      f.clubName,
      `Your place at ${f.eventTitle} is confirmed. Your QR ticket is valid.`,
      { kicker: "You're approved", title: f.eventTitle, subtitle: `Great news, ${esc(firstName(f.participantName))}! Your place is confirmed and your QR ticket is now valid.` },
      detailsBlock(f) + PERFORATION + qrBlock("Confirmed") + stepsBlock() +
        noteBlock("This is the same QR code you received when you registered. " + ATTACHMENTS_NOTE),
    ),
    text: `Hello ${f.participantName},\n\nYour registration is approved: your QR ticket (attached, the same as before) is now valid.\n\n${factsText(f)}\n\nShow it at the entrance, and again when you leave.\n\n${f.clubName}`,
  };
}

export function ticketReplacementEmail(f: EventFacts): EmailContent {
  return {
    subject: `Your new ticket for ${f.eventTitle}`,
    html: layout(
      f.clubName,
      `Your new QR ticket for ${f.eventTitle}. The previous one no longer works.`,
      { kicker: "New ticket", title: f.eventTitle, subtitle: `Hello ${esc(firstName(f.participantName))}, here is your replacement ticket.` },
      banner("Your previous QR code no longer works. Use only the one in this email.", WARNING, WARNING_BG) +
        detailsBlock(f) + PERFORATION + qrBlock("Valid ticket") + stepsBlock() + noteBlock(ATTACHMENTS_NOTE),
    ),
    text: `Hello ${f.participantName},\n\nHere is your replacement ticket. Any earlier ticket no longer works.\n\n${factsText(f)}\n\n${f.clubName}`,
  };
}

export function ticketAccessCodeEmail(clubName: string, eventTitle: string, code: string): EmailContent {
  const digits = code.split("").map((d) =>
    `<td width="44" height="56" align="center" valign="middle" bgcolor="${PAGE}" style="width:44px;height:56px;border:1px solid ${LINE};border-radius:12px;background:${PAGE};font-family:${DISPLAY};font-size:28px;color:${TEXT};">${esc(d)}</td><td width="8" style="width:8px;font-size:0;">&nbsp;</td>`,
  ).join("");
  return {
    subject: `${code} is your ticket access code`,
    html: layout(
      clubName,
      `Your code to open your ticket: ${code}`,
      { kicker: "Ticket access", title: "Your access code", subtitle: `Use it to open your ticket for <strong>${esc(eventTitle)}</strong>.` },
      `<tr><td align="center" style="padding:30px 20px 6px;"><table role="presentation" cellpadding="0" cellspacing="0"><tr>${digits}</tr></table></td></tr>` +
        noteBlock("The code expires in 15 minutes. If you did not ask for it, you can ignore this email: your ticket stays safe."),
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
      { kicker: "See you soon", title: f.eventTitle, subtitle: `Hello ${esc(firstName(f.participantName))}, this is a friendly reminder for your event.` },
      detailsBlock(f) + stepsBlock() + noteBlock("Bring the QR ticket from your confirmation email, on your phone or printed."),
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
      { kicker: "Event cancelled", title: f.eventTitle, subtitle: `Hello ${esc(firstName(f.participantName))}, we are sorry: this event has been cancelled.` },
      banner("Your ticket for this event is no longer valid.", WARNING, WARNING_BG) + detailsBlock(f) + noteBlock("We hope to see you at one of our next events."),
    ),
    text: `Hello ${f.participantName},\n\n${f.eventTitle} has been cancelled. Your ticket is no longer valid.\n\n${f.clubName}`,
  };
}
