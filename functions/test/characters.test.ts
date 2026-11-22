import { test } from "node:test";
import assert from "node:assert/strict";
import {
  COSMETIC_SKINS, DRAGON_DAILY_MAX, dragonCoins, isCosmetic, isRankSkin, lookSkin, PANDA_CHECKIN_BONUS, pandaEquipped, RANK_SKINS,
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
