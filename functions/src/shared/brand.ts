import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { EmailAttachment } from "../notifications/providers";

/** Content-ID of the inline GDG lockup in every email (`<img src="cid:gdg-lockup">`). */
export const LOCKUP_CID = "gdg-lockup";

let cached: Buffer | null | undefined;

/**
 * The supplied GDG On Campus USTO-MB lockup (design/Core/assets/gdg-lockup-160.png, 1221 × 160),
 * copied to functions/assets. Read once; null when the file is missing so emails still send.
 */
export function lockupPng(): Buffer | null {
  if (cached !== undefined) return cached;
  const candidates = [
    join(__dirname, "../../../assets/gdg-lockup.png"), // lib/src/shared → functions/assets
    join(__dirname, "../../assets/gdg-lockup.png"),
    join(process.cwd(), "assets/gdg-lockup.png"),
  ];
  const found = candidates.find((p) => existsSync(p));
  cached = found ? readFileSync(found) : null;
  return cached;
}

/** The inline logo attachment, or nothing when the image is unavailable. */
export function brandAttachments(): EmailAttachment[] {
  const png = lockupPng();
  return png ? [{ filename: "gdg-lockup.png", content: png, contentType: "image/png", contentId: LOCKUP_CID }] : [];
}
