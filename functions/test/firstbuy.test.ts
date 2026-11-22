import { test } from "node:test";
import assert from "node:assert/strict";
import { FIRST_BUY_GRACE_MS, FIRST_BUY_WINDOW_MS, firstBuyBonus } from "../src/catalog";

test("first purchase x2 only within a week of sign-up", () => {
  const joined = 1_700_000_000_000;
  const day = 86_400_000;
  assert.equal(firstBuyBonus(undefined, joined, joined + day), true);
  assert.equal(firstBuyBonus(false, joined, joined + 6 * day), true);
  // 7일이 지나도 영수증 확인이 늦게 오는 하루는 봐줌
  assert.equal(firstBuyBonus(false, joined, joined + FIRST_BUY_WINDOW_MS + FIRST_BUY_GRACE_MS - 1), true);
  assert.equal(firstBuyBonus(false, joined, joined + FIRST_BUY_WINDOW_MS + FIRST_BUY_GRACE_MS), false);
  assert.equal(firstBuyBonus(undefined, joined, joined + 30 * day), false);
});

test("no x2 after the first purchase or without a sign-up time", () => {
  const now = 1_700_000_000_000;
  assert.equal(firstBuyBonus(true, now, now), false);
  assert.equal(firstBuyBonus(undefined, undefined, now), false);
  assert.equal(firstBuyBonus(undefined, "x", now), false);
});
