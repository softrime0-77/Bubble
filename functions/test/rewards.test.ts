import { test } from "node:test";
import assert from "node:assert/strict";
import { missionProgress, nextStreak, potionAdsToday, prevDay, weekKey, weeklyCoins } from "../src/rewards";

test("prevDay crosses month and year", () => {
  assert.equal(prevDay("2026-03-01"), "2026-02-28");
  assert.equal(prevDay("2027-01-01"), "2026-12-31");
});

test("check-in streak continues, wraps after 7, resets after a gap", () => {
  assert.equal(nextStreak(undefined, undefined, "2026-11-21"), 1);
  assert.equal(nextStreak("2026-11-21", 3, "2026-11-21"), null);
  assert.equal(nextStreak("2026-11-20", 3, "2026-11-21"), 4);
  assert.equal(nextStreak("2026-11-20", 7, "2026-11-21"), 1);
  assert.equal(nextStreak("2026-11-18", 5, "2026-11-21"), 1);
});

test("mission progress resets on a new day", () => {
  const today = "2026-11-21";
  assert.deepEqual(missionProgress({ missionDay: today, mPlay: 2, mWin: 1, mRandom: 0 }, today, { play: 1, win: 1, random: 1 }),
    { missionDay: today, mPlay: 3, mWin: 2, mRandom: 1 });
  assert.deepEqual(missionProgress({ missionDay: "2026-11-20", mPlay: 9 }, today, { play: 1, win: 0, random: 0 }),
    { missionDay: today, mPlay: 1, mWin: 0, mRandom: 0, mClaimed: [] });
});

test("weekly ranking coins", () => {
  assert.deepEqual([1, 2, 3, 4, 10, 11, 100, 101].map(weeklyCoins), [1000, 700, 500, 300, 300, 100, 100, 0]);
});

test("weekKey uses ISO weeks in Korean time", () => {
  assert.equal(weekKey(Date.UTC(2026, 10, 23, 0, 0)), "2026-W48"); // 월요일 09:00 KST
  assert.equal(weekKey(Date.UTC(2026, 10, 22, 14, 59)), "2026-W47"); // 일요일 23:59 KST
  assert.equal(weekKey(Date.UTC(2027, 0, 1, 3)), "2026-W53"); // 2027-01-01 금요일은 2026년 53주
});

test("potion ads per day", () => {
  assert.equal(potionAdsToday({ potionAdDay: "2026-11-21", potionAds: 2 }, "2026-11-21"), 2);
  assert.equal(potionAdsToday({ potionAdDay: "2026-11-20", potionAds: 3 }, "2026-11-21"), 0);
});
