import assert from "node:assert/strict";
import test from "node:test";

import { MANUAL_REMINDER_COOLDOWN_HOURS } from "../config";
import { DomainError } from "../lib/domain";
import {
  confirmReturnTransition,
  nextManualReminderAllowedAt,
  requestReturnTransition,
  selectDueReminderKind,
  type LoanStateSnapshot,
} from "../lib/loanState";

const baseLoan: LoanStateSnapshot = {
  status: "active",
  isOpen: true,
  lenderUid: "lender-1",
  borrowerUid: "borrower-1",
  dueDate: new Date("2026-08-21T15:00:00.000Z"),
  reminderState: {
    borrowerDueSoonSentAt: null,
    borrowerDueDateSentAt: null,
    lenderOverdueSentAt: null,
  },
};

function expectDomainError(fn: () => unknown, code: string): void {
  assert.throws(
    fn,
    (error: unknown) => error instanceof DomainError && error.code === code,
  );
}

test("requestReturnTransition marks an active loan as return_requested", () => {
  const now = new Date("2026-08-19T10:00:00.000Z");
  const transition = requestReturnTransition(baseLoan, "borrower-1", now);

  assert.equal(transition.status, "return_requested");
  assert.equal(transition.returnRequestedByUid, "borrower-1");
  assert.equal(transition.returnRequestedAt.toISOString(), now.toISOString());
});

test("confirmReturnTransition requires the other participant to close the loan", () => {
  expectDomainError(
    () => confirmReturnTransition(baseLoan, "borrower-1", new Date("2026-08-20T10:00:00.000Z")),
    "failed-precondition",
  );

  const transition = confirmReturnTransition(
    {
      ...baseLoan,
      status: "return_requested",
      returnRequestedByUid: "borrower-1",
    },
    "lender-1",
    new Date("2026-08-20T10:00:00.000Z"),
  );

  assert.equal(transition.status, "returned");
  assert.equal(transition.isOpen, false);
  assert.equal(transition.returnConfirmedByUid, "lender-1");

  expectDomainError(
    () => confirmReturnTransition(
      {
        ...baseLoan,
        status: "return_requested",
        returnRequestedByUid: "borrower-1",
      },
      "borrower-1",
      new Date("2026-08-20T10:00:00.000Z"),
    ),
    "permission-denied",
  );
});

test("selectDueReminderKind chooses due-soon reminders before the due date", () => {
  const kind = selectDueReminderKind(
    baseLoan,
    new Date("2026-08-19T09:00:00.000Z"),
  );

  assert.equal(kind, "borrower_due_soon");
});

test("selectDueReminderKind chooses overdue reminders after the due time passes", () => {
  const overdueLoan: LoanStateSnapshot = {
    ...baseLoan,
    dueDate: new Date("2026-08-19T08:00:00.000Z"),
  };

  const kind = selectDueReminderKind(
    overdueLoan,
    new Date("2026-08-19T09:00:00.000Z"),
  );

  assert.equal(kind, "lender_overdue");
});

test("nextManualReminderAllowedAt applies the configured cooldown", () => {
  const loan: LoanStateSnapshot = {
    ...baseLoan,
    lastManualReminderAt: new Date("2026-08-19T09:00:00.000Z"),
  };

  const nextAllowedAt = nextManualReminderAllowedAt(
    loan,
    MANUAL_REMINDER_COOLDOWN_HOURS,
  );

  assert.equal(nextAllowedAt?.toISOString(), "2026-08-19T15:00:00.000Z");
});
