import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanName, RENAME_PRICE, renameCost } from "../src/catalog";

test("first rename is free, then a ticket is needed", () => {
  assert.equal(renameCost(undefined, undefined), "free");
  assert.equal(renameCost(false, 0), "free");
  assert.equal(renameCost(true, 1), "ticket");
  assert.equal(renameCost(true, 0), "none");
  assert.equal(renameCost(true, undefined), "none");
  assert.equal(RENAME_PRICE, 100);
});

test("nickname is trimmed to 12 characters", () => {
  assert.equal(cleanName("  말랑고양이  "), "말랑고양이");
  assert.equal(cleanName("가나다라마바사아자차카타파하"), "가나다라마바사아자차카타");
  assert.equal(cleanName("   "), null);
  assert.equal(cleanName(3), null);
});
