import { test } from "node:test";
import assert from "node:assert/strict";
import { applyMove, decodeMove, isLegal, winner, Board as FullBoard, Move, Sets } from "../src/game";

// 테스트에서는 분할 횟수를 생략하면 3회로 채움
type Board = { cat: Sets; ham: Sets; catSplits?: number; hamSplits?: number; catRevives?: number; hamRevives?: number };
const full = (b: Board): FullBoard => ({ catSplits: 3, hamSplits: 3, ...b });

const add = (from: number, toOpponent: boolean, to: number): Move => ({ kind: "add", from, toOpponent, to });

test("attack adds attacker count", () => {
  const b: Board = { cat: [1, 2], ham: [1, 1] };
  assert.deepEqual(applyMove(full(b), "CAT", add(1, true, 0)).board.ham, [3, 1]);
});

test("five or more explodes", () => {
  const r = applyMove(full({ cat: [4, 1], ham: [1, 3] }), "CAT", add(0, true, 1));
  assert.deepEqual(r.board.ham, [1, 0]);
  assert.deepEqual(r.exploded, { side: "HAMSTER", index: 1 });
});

test("dead sets cannot be boosted or attacked", () => {
  const b: Board = { cat: [3, 0], ham: [0, 2] };
  assert.equal(isLegal(full(b), "CAT", add(0, false, 1)), false);
  assert.equal(isLegal(full(b), "CAT", add(0, true, 0)), false);
  assert.equal(isLegal(full(b), "CAT", add(1, true, 1)), false);
  assert.equal(isLegal(full(b), "CAT", add(0, true, 1)), true);
});

test("split revives dead set only when even and other is empty", () => {
  assert.deepEqual(applyMove(full({ cat: [4, 0], ham: [1, 1] }), "CAT", { kind: "split", from: 0 }).board.cat, [2, 2]);
  assert.equal(isLegal(full({ cat: [3, 0], ham: [1, 1] }), "CAT", { kind: "split", from: 0 }), false);
  assert.equal(isLegal(full({ cat: [2, 1], ham: [1, 1] }), "CAT", { kind: "split", from: 0 }), false);
});

test("losing both sets ends the game", () => {
  const r = applyMove(full({ cat: [4, 1], ham: [0, 1] }), "CAT", add(0, true, 1));
  assert.equal(winner(r.board), "CAT");
});

test("decodeMove matches the app format and rejects junk", () => {
  assert.deepEqual(decodeMove("A:1:1:0"), add(1, true, 0));
  assert.deepEqual(decodeMove("S:1"), { kind: "split", from: 1 });
  for (const bad of ["A:2:1:0", "A:0:x:1", "S:", "X", 5, null, "A:0:1"]) assert.equal(decodeMove(bad), null);
});

test("split is limited to three per player", () => {
  let b = full({ cat: [2, 0], ham: [1, 1] });
  b = applyMove(b, "CAT", { kind: "split", from: 0 }).board;
  assert.equal(b.catSplits, 2);
  assert.equal(b.hamSplits, 3);
  assert.equal(isLegal(full({ cat: [4, 0], ham: [1, 1], catSplits: 0 }), "CAT", { kind: "split", from: 0 }), false);
  const last = applyMove(full({ cat: [4, 0], ham: [1, 1], catSplits: 1 }), "CAT", { kind: "split", from: 0 }).board;
  assert.deepEqual(last.cat, [2, 2]);
  assert.equal(last.catSplits, 0);
});

test("revive potion brings back a dead set once", () => {
  const b = full({ cat: [3, 0], ham: [1, 1], catRevives: 1 } as Board);
  assert.equal(isLegal(b, "CAT", { kind: "revive", index: 1 }), true);
  assert.equal(isLegal(b, "CAT", { kind: "revive", index: 0 }), false);
  const after = applyMove(b, "CAT", { kind: "revive", index: 1 }).board;
  assert.deepEqual(after.cat, [3, 1]);
  assert.equal(after.catRevives, 0);
  assert.equal(isLegal(full({ cat: [3, 0], ham: [1, 1] }), "CAT", { kind: "revive", index: 1 }), false);
  assert.deepEqual(decodeMove("R:1"), { kind: "revive", index: 1 });
});

import { cleanCountry, winPoints, rankBonus, rankBonusPercent, revivesForGame, splitPayment, turnLimitAgainst } from "../src/catalog";

test("revives per game are capped at three and follow potions owned", () => {
  assert.equal(revivesForGame(0), 0);
  assert.equal(revivesForGame(undefined), 0);
  assert.equal(revivesForGame(2), 2);
  assert.equal(revivesForGame(10), 3);
});

test("opponent animal shortens my turn", () => {
  assert.equal(turnLimitAgainst(null), 20_000);
  assert.equal(turnLimitAgainst("SHEEP"), 15_000);
  assert.equal(turnLimitAgainst("DOG"), 10_000);
  assert.equal(turnLimitAgainst("GIRAFFE"), 5_000);
  assert.equal(turnLimitAgainst("DRAGON"), 20_000);
});

test("rank bonus follows the new tiers", () => {
  assert.deepEqual(
    [1000, 1001, 1400, 1401, 2000, 2001, 4000, 4001, 7000, 7001, 10000, 10001, 50000, 50001].map(rankBonusPercent),
    [0, 5, 5, 10, 10, 15, 15, 20, 20, 25, 25, 30, 30, 35],
  );
  assert.equal(rankBonus(30, 1000), 0);
  assert.equal(rankBonus(30, 1200), 2); // 1.5 -> 2
  assert.equal(rankBonus(30, 1500), 3);
  assert.equal(rankBonus(30, 2500), 5); // 4.5 -> 5
  assert.equal(rankBonus(30, 6000), 6);
  assert.equal(rankBonus(30, 8000), 8); // 7.5 -> 8
  assert.equal(rankBonus(30, 12000), 9);
  assert.equal(rankBonus(30, 60000), 11); // 10.5 -> 11
  assert.equal(rankBonus(-15, 12000), 0);
});

test("shop payment uses coins first, then score", () => {
  assert.deepEqual(splitPayment(1000, 1500, 0), { fromCoins: 1000, fromScore: 0 });
  assert.deepEqual(splitPayment(1000, 300, 1200), { fromCoins: 300, fromScore: 700 });
  assert.deepEqual(splitPayment(500, 0, 500), { fromCoins: 0, fromScore: 500 });
  assert.equal(splitPayment(1000, 300, 600), null);
});

test("country codes are two uppercase letters", () => {
  assert.equal(cleanCountry("kr"), "KR");
  assert.equal(cleanCountry(" US "), "US");
  assert.equal(cleanCountry("KOR"), null);
  assert.equal(cleanCountry(82), null);
});

test("x3 booster triples the points won, rank bonus included", () => {
  assert.equal(winPoints(30, 0, false), 30);
  assert.equal(winPoints(30, 0, true), 90);
  assert.equal(winPoints(30, 9, true), 117);
  assert.equal(winPoints(10, 3, true), 39);
});
