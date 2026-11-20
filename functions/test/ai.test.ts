import { test } from "node:test";
import assert from "node:assert/strict";
import { chooseMove } from "../src/ai";
import { applyMove, Board, winner } from "../src/game";

const board = (cat: [number, number], ham: [number, number]): Board => ({ cat, ham, catSplits: 3, hamSplits: 3 });

test("hard AI takes an immediate win", () => {
  const b = board([4, 0], [0, 1]);
  const m = chooseMove(b, "CAT", "HARD", () => 0.5);
  assert.equal(winner(applyMove(b, "CAT", m).board), "CAT");
});

test("AI always returns a legal move through full games", () => {
  for (let g = 0; g < 20; g++) {
    let b = board([1, 1], [1, 1]);
    let side: "CAT" | "HAMSTER" = "CAT";
    for (let turn = 0; turn < 200 && !winner(b); turn++) {
      const lv = (["EASY", "NORMAL", "HARD"] as const)[(g + turn) % 3];
      b = applyMove(b, side, chooseMove(b, side, lv)).board;
      side = side === "CAT" ? "HAMSTER" : "CAT";
    }
  }
});
