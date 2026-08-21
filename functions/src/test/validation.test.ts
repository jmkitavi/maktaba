import assert from "node:assert/strict";
import test from "node:test";

import { formatLoanCode, generateLoanCode, normalizeLoanCode } from "../lib/codes";
import { DomainError } from "../lib/domain";
import {
  validateAcceptLoanInviteInput,
  validateCopyIdInput,
  validateCreateLoanInviteInput,
  validateResolveLoanInviteInput,
} from "../lib/validation";

const fixedNow = new Date("2026-08-19T09:00:00.000Z");

function expectDomainError(fn: () => unknown, code: string): void {
  assert.throws(
    fn,
    (error: unknown) => error instanceof DomainError && error.code === code,
  );
}

test("generateLoanCode returns a display-friendly 8-character code", () => {
  const code = generateLoanCode(() => Uint8Array.from([0, 1, 2, 3, 4, 5, 6, 7]));

  assert.match(code, /^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.equal(normalizeLoanCode(code), "ABCDEFGH");
  assert.equal(formatLoanCode("ABCDEFGH"), code);
});

test("validateCreateLoanInviteInput accepts Android field names and trims borrowerDisplayName", () => {
  const parsed = validateCreateLoanInviteInput(
    {
      copyId: "copy_123",
      dueAtMillis: Date.parse("2026-08-21T12:00:00.000Z"),
      borrowerDisplayName: "  Maya Chen  ",
    },
    fixedNow,
  );

  assert.equal(parsed.copyId, "copy_123");
  assert.equal(parsed.catalogBookId, undefined);
  assert.equal(parsed.borrowerDisplayName, "Maya Chen");
  assert.equal(parsed.dueAt.toISOString(), "2026-08-21T12:00:00.000Z");
});

test("validateCreateLoanInviteInput rejects dates in the past", () => {
  expectDomainError(
    () =>
      validateCreateLoanInviteInput(
        {
          copyId: "copy_123",
          dueAtMillis: Date.parse("2026-08-19T09:30:00.000Z"),
        },
        fixedNow,
      ),
    "invalid-argument",
  );
});

test("validateCreateLoanInviteInput accepts an optional catalogBookId hint", () => {
  const parsed = validateCreateLoanInviteInput(
    {
      copyId: "copy_123456",
      catalogBookId: "hobbit",
      dueAtMillis: Date.parse("2026-08-21T12:00:00.000Z"),
    },
    fixedNow,
  );

  assert.equal(parsed.copyId, "copy_123456");
  assert.equal(parsed.catalogBookId, "hobbit");
});

test("validateCreateLoanInviteInput accepts Firestore auto-generated copy ids", () => {
  const parsed = validateCreateLoanInviteInput(
    {
      copyId: "AbC12dEfGhIJkLmNopQr",
      dueAtMillis: Date.parse("2026-08-21T12:00:00.000Z"),
    },
    fixedNow,
  );

  assert.equal(parsed.copyId, "AbC12dEfGhIJkLmNopQr");
});

test("validateResolveLoanInviteInput normalizes inviteCode payloads", () => {
  const parsed = validateResolveLoanInviteInput({ inviteCode: "ab2c-d3ef" });
  assert.equal(parsed.normalizedInviteCode, "AB2CD3EF");
});

test("validateAcceptLoanInviteInput accepts the canonical loanId payload", () => {
  const parsed = validateAcceptLoanInviteInput({ loanId: "loan_123456" });
  assert.equal(parsed.loanId, "loan_123456");
});

test("validateAcceptLoanInviteInput requires a loanId or an inviteCode", () => {
  expectDomainError(() => validateAcceptLoanInviteInput({}), "invalid-argument");
});

test("validateCopyIdInput accepts canonical copy ids", () => {
  assert.deepEqual(validateCopyIdInput({ copyId: "copy_123456" }), {
    copyId: "copy_123456",
  });
  expectDomainError(() => validateCopyIdInput({ copyId: "../copy" }), "invalid-argument");
});
