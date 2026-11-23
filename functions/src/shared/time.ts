export function isValidTimeZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

function safeZone(zone: string): string {
  return isValidTimeZone(zone) ? zone : "UTC";
}

/** "Saturday, 14 November 2026" in the event's own time zone. */
export function formatEventDate(ms: number, zone: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: safeZone(zone), weekday: "long", day: "numeric", month: "long", year: "numeric",
  }).format(new Date(ms));
}

/** The club's zone (Africa/Algiers) is shown to people as "Oran"; other zones keep their id. */
export function zoneLabel(zone: string): string {
  const id = safeZone(zone);
  return id === "Africa/Algiers" ? "Oran" : id;
}

/** "09:00 – 12:30 (Oran)" in the event's own time zone. */
export function formatEventTime(startMs: number, endMs: number, zone: string): string {
  const format = new Intl.DateTimeFormat("en-GB", { timeZone: safeZone(zone), hour: "2-digit", minute: "2-digit" });
  return `${format.format(new Date(startMs))} – ${format.format(new Date(endMs))} (${zoneLabel(zone)})`;
}
