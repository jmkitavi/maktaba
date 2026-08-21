import assert from "node:assert/strict";
import test from "node:test";

import { bookRemovalBlock } from "../lib/bookRemoval";
import type {
  LendingSlotDocument,
  LoanDocument,
  LoanInviteDocument,
} from "../models";

const slot = {
  state: "invite_pending",
} as LendingSlotDocument;
const future = {
  toDate: () => new Date("2026-08-22T00:00:00.000Z"),
};
const past = {
  toDate: () => new Date("2026-08-20T00:00:00.000Z"),
};
const now = new Date("2026-08-21T00:00:00.000Z");

test("blocks removal for a pending invitation", () => {
  assert.equal(
    bookRemovalBlock(
      slot,
      { status: "pending", expiresAt: future } as LoanInviteDocument,
      null,
      now,
    ),
    "pending_invite",
  );
});

test("blocks removal for an open loan", () => {
  assert.equal(
    bookRemovalBlock(
      { state: "loan_active" } as LendingSlotDocument,
      null,
      { isOpen: true } as LoanDocument,
    ),
    "active_loan",
  );
});

test("allows removal when slot references are stale or closed", () => {
  assert.equal(
    bookRemovalBlock(slot, { status: "cancelled" } as LoanInviteDocument, null),
    null,
  );
  assert.equal(
    bookRemovalBlock(
      { state: "loan_active" } as LendingSlotDocument,
      null,
      { isOpen: false } as LoanDocument,
    ),
    null,
  );
  assert.equal(
    bookRemovalBlock(
      slot,
      { status: "pending", expiresAt: past } as LoanInviteDocument,
      null,
      now,
    ),
    null,
  );
});
