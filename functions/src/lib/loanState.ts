import { DUE_SOON_DAYS, MANUAL_REMINDER_COOLDOWN_HOURS } from "../config";
import type { LoanStatus } from "../models";
import { assertDomain } from "./domain";

export interface ReminderStateSnapshot {
  borrowerDueSoonSentAt?: Date | null;
  borrowerDueDateSentAt?: Date | null;
  lenderOverdueSentAt?: Date | null;
}

export interface LoanStateSnapshot {
  status: LoanStatus;
  isOpen: boolean;
  lenderUid: string;
  borrowerUid: string;
  dueDate: Date;
  returnRequestedByUid?: string | null;
  lastManualReminderAt?: Date | null;
  reminderState?: ReminderStateSnapshot;
}

export type LoanRole = "lender" | "borrower";
export type DueReminderKind =
  | "borrower_due_soon"
  | "borrower_due_today"
  | "lender_overdue";

export function getParticipantRole(loan: LoanStateSnapshot, uid: string): LoanRole | null {
  if (loan.lenderUid === uid) {
    return "lender";
  }

  if (loan.borrowerUid === uid) {
    return "borrower";
  }

  return null;
}

export function assertParticipantRole(loan: LoanStateSnapshot, uid: string): LoanRole {
  const role = getParticipantRole(loan, uid);
  assertDomain(role !== null, "permission-denied", "Only loan participants can do that.");
  return role;
}

export function otherParticipantUid(loan: LoanStateSnapshot, uid: string): string {
  const role = assertParticipantRole(loan, uid);
  return role === "lender" ? loan.borrowerUid : loan.lenderUid;
}

export function requestReturnTransition(
  loan: LoanStateSnapshot,
  actorUid: string,
  now: Date,
) {
  assertParticipantRole(loan, actorUid);
  assertDomain(loan.isOpen, "failed-precondition", "Loan is closed.");
  assertDomain(
    loan.status !== "return_requested",
    "failed-precondition",
    "A return has already been requested.",
  );

  return {
    status: "return_requested" as const,
    returnRequestedByUid: actorUid,
    returnRequestedAt: now,
    updatedAt: now,
  };
}

export function confirmReturnTransition(
  loan: LoanStateSnapshot,
  actorUid: string,
  now: Date,
) {
  assertParticipantRole(loan, actorUid);
  assertDomain(
    loan.status === "return_requested" && loan.returnRequestedByUid !== null,
    "failed-precondition",
    "A return must be requested before it can be confirmed.",
  );
  assertDomain(
    loan.returnRequestedByUid !== actorUid,
    "permission-denied",
    "The other participant must confirm the return.",
  );
  assertDomain(loan.isOpen, "failed-precondition", "Loan is closed.");

  return {
    status: "returned" as const,
    isOpen: false as const,
    returnedAt: now,
    returnConfirmedByUid: actorUid,
    updatedAt: now,
  };
}

export function nextManualReminderAllowedAt(
  loan: LoanStateSnapshot,
  cooldownHours = MANUAL_REMINDER_COOLDOWN_HOURS,
): Date | null {
  if (!loan.lastManualReminderAt) {
    return null;
  }

  return new Date(loan.lastManualReminderAt.getTime() + (cooldownHours * 3_600_000));
}

export function canSendManualReminder(
  loan: LoanStateSnapshot,
  now: Date,
  cooldownHours = MANUAL_REMINDER_COOLDOWN_HOURS,
): boolean {
  if (!loan.isOpen || loan.status === "returned") {
    return false;
  }

  const nextAllowed = nextManualReminderAllowedAt(loan, cooldownHours);
  return nextAllowed === null || nextAllowed.getTime() <= now.getTime();
}

function utcMidnight(date: Date): number {
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

export function differenceInCalendarDaysUtc(laterDate: Date, earlierDate: Date): number {
  return Math.round((utcMidnight(laterDate) - utcMidnight(earlierDate)) / 86_400_000);
}

export function selectDueReminderKind(
  loan: LoanStateSnapshot,
  now: Date,
): DueReminderKind | null {
  if (!loan.isOpen || loan.status === "returned") {
    return null;
  }

  const reminderState = loan.reminderState ?? {};

  if (now.getTime() > loan.dueDate.getTime()) {
    if (!reminderState.lenderOverdueSentAt) {
      return "lender_overdue";
    }
    return null;
  }

  const daysUntilDue = differenceInCalendarDaysUtc(loan.dueDate, now);
  if (daysUntilDue === 0) {
    if (!reminderState.borrowerDueDateSentAt) {
      return "borrower_due_today";
    }
    return null;
  }

  if (
    daysUntilDue > 0 &&
    daysUntilDue <= DUE_SOON_DAYS &&
    !reminderState.borrowerDueSoonSentAt
  ) {
    return "borrower_due_soon";
  }

  return null;
}
