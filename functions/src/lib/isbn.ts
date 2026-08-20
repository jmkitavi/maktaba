import { DomainError } from "./domain";

export interface NormalizedIsbn {
  isbn13: string;
  isbn10: string | null;
}

export function compactIsbn(value: string): string {
  return value.replace(/[\s-]/g, "").toUpperCase();
}

export function isValidIsbn10(value: string): boolean {
  const isbn = compactIsbn(value);
  if (!/^\d{9}[\dX]$/.test(isbn)) {
    return false;
  }
  const sum = [...isbn].reduce((total, char, index) => {
    const digit = char === "X" ? 10 : Number(char);
    return total + ((10 - index) * digit);
  }, 0);
  return sum % 11 === 0;
}

export function isValidIsbn13(value: string): boolean {
  const isbn = compactIsbn(value);
  if (!/^\d{13}$/.test(isbn)) {
    return false;
  }
  const sum = [...isbn].reduce(
    (total, char, index) => total + (Number(char) * (index % 2 === 0 ? 1 : 3)),
    0,
  );
  return sum % 10 === 0;
}

export function isbn10To13(value: string): string {
  const isbn10 = compactIsbn(value);
  if (!isValidIsbn10(isbn10)) {
    throw new DomainError("invalid-argument", "Enter a valid ISBN-10.");
  }
  const body = `978${isbn10.slice(0, 9)}`;
  const sum = [...body].reduce(
    (total, char, index) => total + (Number(char) * (index % 2 === 0 ? 1 : 3)),
    0,
  );
  return `${body}${(10 - (sum % 10)) % 10}`;
}

export function normalizeIsbn(value: unknown): NormalizedIsbn {
  if (typeof value !== "string") {
    throw new DomainError("invalid-argument", "ISBN must be a string.");
  }
  const compact = compactIsbn(value);
  if (compact.length === 10 && isValidIsbn10(compact)) {
    return { isbn13: isbn10To13(compact), isbn10: compact };
  }
  if (compact.length === 13 && isValidIsbn13(compact)) {
    return { isbn13: compact, isbn10: null };
  }
  throw new DomainError("invalid-argument", "Enter a valid ISBN-10 or ISBN-13.");
}
