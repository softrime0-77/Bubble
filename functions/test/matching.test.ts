import { test } from "node:test";
import assert from "node:assert/strict";
import { pickOpponent, scoreWindow } from "../src/matching";

const now = 1_000_000;
const w = (id: string, score: number, waitedMs: number) => ({ id, score, createdAt: now - waitedMs });

test("window widens with waiting time and opens after 20 seconds", () => {
  assert.equal(scoreWindow(0), 150);
  assert.equal(scoreWindow(10_000), 400);
  assert.equal(scoreWindow(19_999), 625);
  assert.equal(scoreWindow(20_000), Number.POSITIVE_INFINITY);
});

test("picks the closest score within the window", () => {
  const me = w("me", 1200, 0);
  const pick = pickOpponent(me, [w("a", 1500, 0), w("b", 1260, 0), w("c", 1100, 0)], now);
  assert.equal(pick?.id, "b");
});

test("does not match far scores until someone has waited long enough", () => {
  const me = w("me", 1000, 0);
  assert.equal(pickOpponent(me, [w("far", 3000, 5_000)], now), null);
  assert.equal(pickOpponent(me, [w("far", 3000, 21_000)], now)?.id, "far");
});

test("never matches yourself", () => {
  const me = w("me", 1000, 0);
  assert.equal(pickOpponent(me, [w("me", 1000, 0)], now), null);
});
