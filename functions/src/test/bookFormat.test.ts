import assert from "node:assert/strict";
import test from "node:test";

import {
  assertBookFormatIsLendable,
  classifyBookFormat,
  resolveBookFormat,
  suggestPhysicalEditionIsbn13,
} from "../lib/bookFormat";
import { DomainError } from "../lib/domain";

test("classifies digital binding variants", () => {
  for (const binding of [
    "ebook",
    "E-Book",
    "EPUB",
    "Kindle Edition",
    "Digital",
    "Electronic Resource",
  ]) {
    assert.equal(classifyBookFormat(binding), "DIGITAL", binding);
  }
});

test("classifies physical and unknown bindings conservatively", () => {
  assert.equal(classifyBookFormat("Paperback"), "PHYSICAL");
  assert.equal(classifyBookFormat("Hardcover"), "PHYSICAL");
  assert.equal(classifyBookFormat(null), "UNKNOWN");
  assert.equal(classifyBookFormat("Unknown"), "UNKNOWN");
  assert.equal(resolveBookFormat(undefined, "DIGITAL"), "DIGITAL");
  assert.equal(resolveBookFormat(undefined, "not-a-format"), "UNKNOWN");
});

test("suggests only the known physical edition for a digital ISBN", () => {
  assert.equal(
    suggestPhysicalEditionIsbn13("9781473591516", "DIGITAL"),
    "9781785043666",
  );
  assert.equal(suggestPhysicalEditionIsbn13("9781473591516", "PHYSICAL"), undefined);
  assert.equal(suggestPhysicalEditionIsbn13("9780000000000", "DIGITAL"), undefined);
});

test("digital editions cannot enter the loan workflow", () => {
  assert.throws(
    () => assertBookFormatIsLendable("DIGITAL"),
    (error: unknown) =>
      error instanceof DomainError && error.code === "failed-precondition",
  );
  assert.doesNotThrow(() => assertBookFormatIsLendable("PHYSICAL"));
  assert.doesNotThrow(() => assertBookFormatIsLendable("UNKNOWN"));
});
