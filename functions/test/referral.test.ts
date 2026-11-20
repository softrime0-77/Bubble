import { test } from "node:test";
import assert from "node:assert/strict";
import { CODE_LENGTH, normalizeCode, randomCode } from "../src/referral";

test("random codes use the safe alphabet", () => {
  for (let i = 0; i < 200; i++) {
    const c = randomCode();
    assert.equal(c.length, CODE_LENGTH);
    assert.equal(normalizeCode(c), c);
  }
});

test("normalizeCode trims, uppercases and rejects bad input", () => {
  assert.equal(normalizeCode(" abc 234 "), "ABC234");
  assert.equal(normalizeCode("ABC23"), null);
  assert.equal(normalizeCode("ABC0O1"), null);
  assert.equal(normalizeCode(123456), null);
});
