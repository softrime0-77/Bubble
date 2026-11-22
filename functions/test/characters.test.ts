import { test } from "node:test";
import assert from "node:assert/strict";
import {
  COSMETIC_SKINS, DRAGON_DAILY_MAX, dragonCoins, isCosmetic, isRankSkin, lookSkin, PANDA_CHECKIN_BONUS, pandaEquipped, RANK_SKINS,
  RABBIT_DAILY_MAX, rabbitCoins,
} from "../src/catalog";

test("new characters prices", () => {
  assert.deepEqual(
    ["OCTOPUS", "PENGUIN", "PANDA", "DRAGON", "RABBIT"].map((k) => COSMETIC_SKINS[k]),
    [400, 400, 500, 700, 600],
  );
  assert.equal(isCosmetic("UNICORN"), false);
  assert.equal(isRankSkin("UNICORN"), true);
  assert.equal(RANK_SKINS.UNICORN, 10001);
});

test("unicorn shows only at diamond rank", () => {
  assert.equal(lookSkin("UNICORN", [], 10001), "UNICORN");
  assert.equal(lookSkin("UNICORN", [], 10000), null);
  assert.equal(lookSkin("UNICORN", ["UNICORN"], 5000), null);
});

test("panda bonus needs the owned panda equipped", () => {
  assert.equal(PANDA_CHECKIN_BONUS, 5);
  assert.equal(pandaEquipped("PANDA", ["PANDA"]), true);
  assert.equal(pandaEquipped("PANDA", []), false);
  assert.equal(pandaEquipped("DOG", ["PANDA"]), false);
});

test("dragon coins capped per day", () => {
  assert.equal(dragonCoins(0), 2);
  assert.equal(dragonCoins(8), 2);
  assert.equal(dragonCoins(9), 1);
  assert.equal(dragonCoins(DRAGON_DAILY_MAX), 0);
});

test("rabbit streak rewards: 3 -> 5, 5 -> 15, 6+ -> 2, daily max 30", () => {
  assert.equal(rabbitCoins(1, 0), 0);
  assert.equal(rabbitCoins(2, 0), 0);
  assert.equal(rabbitCoins(3, 0), 5);
  assert.equal(rabbitCoins(4, 0), 0);
  assert.equal(rabbitCoins(5, 0), 15);
  assert.equal(rabbitCoins(6, 0), 2);
  // 10연승: 5 + 15 + 2×5 = 30 → 하루 최대
  let got = 0;
  for (let s = 1; s <= 10; s++) got += rabbitCoins(s, got);
  assert.equal(got, RABBIT_DAILY_MAX);
  assert.equal(rabbitCoins(11, got), 0);
  // 끊겼다가 다시 3연승이면 또 5 (한도 안에서)
  assert.equal(rabbitCoins(3, 20), 5);
  assert.equal(rabbitCoins(3, 27), 3);
});
