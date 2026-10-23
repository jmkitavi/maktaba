import { MAX_LOAN_DAYS, MIN_LOAN_HOURS } from "../config";
import { assertValidLoanCode } from "./codes";
import { assertDomain } from "./domain";

export interface CreateLoanInviteInput {
  copyId: string;
  catalogBookId?: string;
  dueAt: Date;
  borrowerDisplayName?: string;
}

export interface ResolveLoanInviteInput {
  normalizedInviteCode: string;
}

export interface AcceptLoanInviteInput {
  loanId?: string;
  normalizedInviteCode?: string;
  borrowerDisplayName?: string;
}

export interface LoanIdInput {
  loanId: string;
}

export interface LoanWorkflowIdInput {
  loanId: string;
}

export interface InviteIdInput {
  inviteId: string;
}

export interface CopyIdInput {
  copyId: string;
}

export interface UserProfileInput {
  displayName?: string;
}

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  assertDomain(
    value !== null && typeof value === "object" && !Array.isArray(value),
    "invalid-argument",
    "Expected an object payload.",
  );
  return value as UnknownRecord;
}

function parseRequiredId(value: unknown, fieldName: string): string {
  assertDomain(typeof value === "string", "invalid-argument", `${fieldName} must be a string.`);
  const trimmed = value.trim();
  assertDomain(
    /^[A-Za-z0-9_-]{6,128}$/.test(trimmed),
    "invalid-argument",
    `${fieldName} is invalid.`,
  );
  return trimmed;
}

function parseCatalogBookId(value: unknown): string {
  assertDomain(typeof value === "string", "invalid-argument", "catalogBookId must be a string.");
  const trimmed = value.trim();
  assertDomain(
    /^[a-z0-9][a-z0-9_-]{1,63}$/.test(trimmed),
    "invalid-argument",
    "catalogBookId is invalid.",
  );
  return trimmed;
}

function parseCopyId(value: unknown): string {
  assertDomain(typeof value === "string", "invalid-argument", "copyId must be a string.");
  const trimmed = value.trim();
  assertDomain(
    /^[A-Za-z0-9_-]{6,128}$/.test(trimmed),
    "invalid-argument",
    "copyId is invalid.",
  );
  return trimmed;
}

export function parseOptionalDisplayName(
  value: unknown,
  fieldName = "displayName",
): string | undefined {
  if (value == null) {
    return undefined;
  }

  assertDomain(typeof value === "string", "invalid-argument", `${fieldName} must be a string.`);
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  assertDomain(
    trimmed.length <= 80,
    "invalid-argument",
    `${fieldName} must be 80 characters or fewer.`,
  );
  return trimmed;
}

function parseDateValue(value: unknown): Date {
  if (value instanceof Date) {
    return value;
  }

  if (typeof value === "number" || typeof value === "string") {
    return new Date(value);
  }

  if (value !== null && typeof value === "object") {
    const record = value as Record<string, unknown>;
    const seconds =
      typeof record["seconds"] === "number"
        ? record["seconds"]
        : typeof record["_seconds"] === "number"
          ? record["_seconds"]
          : undefined;

    if (seconds !== undefined) {
      const nanoseconds =
        typeof record["nanoseconds"] === "number"
          ? record["nanoseconds"]
          : typeof record["_nanoseconds"] === "number"
            ? record["_nanoseconds"]
            : 0;

      return new Date((seconds * 1000) + Math.floor(nanoseconds / 1_000_000));
    }
  }

  return new Date(Number.NaN);
}

export function validateCreateLoanInviteInput(
  data: unknown,
  now: Date = new Date(),
): CreateLoanInviteInput {
  const record = asRecord(data);
  const copyId = parseCopyId(record["copyId"] ?? record["catalogBookId"]);
  const catalogBookId =
    record["catalogBookId"] == null
      ? undefined
      : parseCatalogBookId(record["catalogBookId"]);

  const dueAt = parseDateValue(record["dueAtMillis"] ?? record["dueDate"]);

  assertDomain(
    !Number.isNaN(dueAt.getTime()),
    "invalid-argument",
    "dueAtMillis must be a valid future timestamp.",
  );
  assertDomain(
    dueAt.getTime() >= now.getTime() + (MIN_LOAN_HOURS * 3_600_000),
    "invalid-argument",
    `dueAtMillis must be at least ${MIN_LOAN_HOURS} hour(s) in the future.`,
  );
  assertDomain(
    dueAt.getTime() <= now.getTime() + (MAX_LOAN_DAYS * 86_400_000),
    "invalid-argument",
    `dueAtMillis must be within ${MAX_LOAN_DAYS} days.`,
  );

  return {
    copyId,
    catalogBookId,
    dueAt,
    borrowerDisplayName: parseOptionalDisplayName(
      record["borrowerDisplayName"] ?? record["borrowerName"],
      "borrowerDisplayName",
    ),
  };
}

export function validateResolveLoanInviteInput(data: unknown): ResolveLoanInviteInput {
  const record = asRecord(data);
  return {
    normalizedInviteCode: assertValidLoanCode(record["inviteCode"] ?? record["code"]),
  };
}

export function validateAcceptLoanInviteInput(data: unknown): AcceptLoanInviteInput {
  const record = asRecord(data);

  const loanId =
    record["loanId"] == null
      ? record["inviteId"] == null
        ? undefined
        : parseRequiredId(record["inviteId"], "inviteId")
      : parseRequiredId(record["loanId"], "loanId");
  const normalizedInviteCode =
    record["inviteCode"] == null
      ? record["code"] == null
        ? undefined
        : assertValidLoanCode(record["code"])
      : assertValidLoanCode(record["inviteCode"]);

  assertDomain(
    Boolean(loanId || normalizedInviteCode),
    "invalid-argument",
    "Provide either loanId or inviteCode.",
  );

  return {
    loanId,
    normalizedInviteCode,
    borrowerDisplayName: parseOptionalDisplayName(
      record["borrowerDisplayName"],
      "borrowerDisplayName",
    ),
  };
}

export function validateLoanIdInput(data: unknown): LoanIdInput {
  const record = asRecord(data);
  return {
    loanId: parseRequiredId(record["loanId"], "loanId"),
  };
}

export function validateInviteIdInput(data: unknown): InviteIdInput {
  const record = asRecord(data);
  return {
    inviteId: parseRequiredId(record["inviteId"], "inviteId"),
  };
}

export function validateCopyIdInput(data: unknown): CopyIdInput {
  const record = asRecord(data);
  return {
    copyId: parseCopyId(record["copyId"]),
  };
}

export function validateLoanWorkflowIdInput(data: unknown): LoanWorkflowIdInput {
  const record = asRecord(data);
  return {
    loanId: parseRequiredId(record["loanId"] ?? record["inviteId"], "loanId"),
  };
}

export function validateUserProfileInput(data: unknown): UserProfileInput {
  if (data == null) {
    return {};
  }

  const record = asRecord(data);
  return {
    displayName: parseOptionalDisplayName(record["displayName"]),
  };
}
