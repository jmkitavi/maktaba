import { DomainError } from "./domain";
import type { BookFormat } from "../models";

const KNOWN_PHYSICAL_EDITIONS: Readonly<Record<string, string>> = {
  "9781473591516": "9781785043666",
};

export function classifyBookFormat(binding: string | null | undefined): BookFormat {
  const normalized = binding?.trim().toLowerCase();
  if (!normalized || ["unknown", "unspecified", "n/a", "na", "none"].includes(normalized)) {
    return "UNKNOWN";
  }

  if (
    /\be[\s-]?book\b/.test(normalized) ||
    /\bepub\b/.test(normalized) ||
    /\bkindle\b/.test(normalized) ||
    /\bdigital\b/.test(normalized) ||
    /\belectronic\s+resource\b/.test(normalized)
  ) {
    return "DIGITAL";
  }

  return "PHYSICAL";
}

export function resolveBookFormat(
  binding: string | null | undefined,
  storedFormat?: unknown,
): BookFormat {
  const classified = classifyBookFormat(binding);
  if (classified !== "UNKNOWN") {
    return classified;
  }
  return storedFormat === "PHYSICAL" || storedFormat === "DIGITAL"
    ? storedFormat
    : "UNKNOWN";
}

export function suggestPhysicalEditionIsbn13(
  isbn13: string | null | undefined,
  format: BookFormat,
): string | undefined {
  return format === "DIGITAL" && isbn13
    ? KNOWN_PHYSICAL_EDITIONS[isbn13]
    : undefined;
}

export function assertBookFormatIsLendable(format: BookFormat): void {
  if (format === "DIGITAL") {
    throw new DomainError(
      "failed-precondition",
      "Digital editions cannot be lent.",
    );
  }
}
