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

import { revivesForGame, turnLimitAgainst } from "../src/catalog";

test("revives per game are capped at three and follow potions owned", () => {
  assert.equal(revivesForGame(0), 0);
  assert.equal(revivesForGame(undefined), 0);
  assert.equal(revivesForGame(2), 2);
  assert.equal(revivesForGame(10), 3);
});

test("opponent animal shortens my turn", () => {
  assert.equal(turnLimitAgainst(null), 30_000);
  assert.equal(turnLimitAgainst("SHEEP"), 20_000);
  assert.equal(turnLimitAgainst("DOG"), 15_000);
  assert.equal(turnLimitAgainst("GIRAFFE"), 10_000);
  assert.equal(turnLimitAgainst("DRAGON"), 30_000);
});
