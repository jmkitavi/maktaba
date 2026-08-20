import { randomBytes } from "node:crypto";

import { assertDomain } from "./domain";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 8;

export type RandomByteFactory = (size: number) => Uint8Array;

export function normalizeLoanCode(code: string): string {
  return code.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

export function assertValidLoanCode(value: unknown): string {
  assertDomain(typeof value === "string", "invalid-argument", "Loan code must be a string.");
  const normalized = normalizeLoanCode(value.trim());
  assertDomain(
    /^[A-HJ-NP-Z2-9]{8}$/.test(normalized),
    "invalid-argument",
    "Loan code must contain 8 letters or numbers.",
  );
  return normalized;
}

export function formatLoanCode(normalizedCode: string): string {
  const normalized = normalizeLoanCode(normalizedCode);
  assertDomain(
    /^[A-HJ-NP-Z2-9]{8}$/.test(normalized),
    "invalid-argument",
    "Loan code must contain 8 letters or numbers.",
  );

  return `${normalized.slice(0, 4)}-${normalized.slice(4)}`;
}

export function generateLoanCode(randomFactory: RandomByteFactory = randomBytes): string {
  const bytes = randomFactory(CODE_LENGTH);
  assertDomain(bytes.length >= CODE_LENGTH, "internal", "Random generator returned too few bytes.");

  let normalized = "";
  for (let index = 0; index < CODE_LENGTH; index += 1) {
    const nextChar = CODE_ALPHABET[bytes[index]! % CODE_ALPHABET.length];
    assertDomain(nextChar !== undefined, "internal", "Unable to generate loan code.");
    normalized += nextChar;
  }

  return formatLoanCode(normalized);
}
