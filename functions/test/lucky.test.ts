import { test } from "node:test";
import assert from "node:assert/strict";
import { COSMETIC_SKINS, LUCKY_DAILY_MAX, LUCKY_SKIN, isCosmetic, lookSkin, luckyRoll } from "../src/catalog";

test("lucky cat is a 1000-point permanent skin", () => {
  assert.equal(COSMETIC_SKINS[LUCKY_SKIN], 1000);
  assert.equal(isCosmetic(LUCKY_SKIN), true);
  assert.equal(lookSkin(LUCKY_SKIN, [LUCKY_SKIN]), LUCKY_SKIN);
  assert.equal(lookSkin(LUCKY_SKIN, []), null);
});

test("lucky roll: 50% and at most 30 a day", () => {
  assert.equal(luckyRoll(0, 0.49), true);
  assert.equal(luckyRoll(0, 0.5), false);
  assert.equal(luckyRoll(LUCKY_DAILY_MAX - 1, 0), true);
  assert.equal(luckyRoll(LUCKY_DAILY_MAX, 0), false);
  // 확률이 실제로 50% 근처인지
  let hits = 0;
  for (let i = 0; i < 100_000; i++) if (luckyRoll(0, Math.random())) hits++;
  assert.ok(Math.abs(hits / 100_000 - 0.5) < 0.01);
});
