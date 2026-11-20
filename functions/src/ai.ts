// 앱의 Ai.kt와 같은 알파-베타 미니맥스 AI. 서버에서 AI 대전 상대의 수를 둡니다.

import { applyMove, Board, isLegal, Move, opponent, Side, sets, winner } from "./game";

export type AiLevel = "EASY" | "NORMAL" | "HARD";

const LEVELS: Record<AiLevel, { depth: number; mistakeRate: number }> = {
  EASY: { depth: 1, mistakeRate: 0.45 },
  NORMAL: { depth: 4, mistakeRate: 0.12 },
  HARD: { depth: 9, mistakeRate: 0 },
};

export const isAiLevel = (x: unknown): x is AiLevel => x === "EASY" || x === "NORMAL" || x === "HARD";

const WIN = 100_000;

export function legalMoves(b: Board, side: Side): Move[] {
  if (winner(b) !== null) return [];
  const all: Move[] = [];
  for (let from = 0; from < 2; from++) {
    for (let to = 0; to < 2; to++) {
      all.push({ kind: "add", from, toOpponent: true, to }, { kind: "add", from, toOpponent: false, to });
    }
    all.push({ kind: "split", from }, { kind: "revive", index: from });
  }
  return all.filter((m) => isLegal(b, side, m));
}

function evaluate(b: Board, side: Side): number {
  const value = (v: number[]) =>
    v.reduce((acc, n) => acc + (n > 0 ? 100 : 0) + ([0, 6, 10, 8, -4][n] ?? 0), 0);
  return value(sets(b, side)) - value(sets(b, opponent(side)));
}

function search(b: Board, toMove: Side, depth: number, alphaIn: number, beta: number): number {
  const w = winner(b);
  if (w) return w === toMove ? WIN + depth : -WIN - depth;
  if (depth <= 0) return evaluate(b, toMove);
  let alpha = alphaIn;
  let best = -Infinity;
  for (const m of legalMoves(b, toMove)) {
    const score = -search(applyMove(b, toMove, m).board, opponent(toMove), depth - 1, -beta, -alpha);
    if (score > best) best = score;
    if (best > alpha) alpha = best;
    if (alpha >= beta) break;
  }
  return best;
}

export function chooseMove(b: Board, side: Side, level: AiLevel, rand: () => number = Math.random): Move {
  const moves = legalMoves(b, side);
  if (moves.length === 0) throw new Error("no legal moves");
  const { depth, mistakeRate } = LEVELS[level];
  if (rand() < mistakeRate) return moves[Math.floor(rand() * moves.length)];
  let best = -Infinity;
  let bestMoves: Move[] = [];
  for (const m of moves) {
    const score = -search(applyMove(b, side, m).board, opponent(side), depth - 1, -WIN * 2, WIN * 2);
    if (score > best) {
      best = score;
      bestMoves = [m];
    } else if (score === best) {
      bestMoves.push(m);
    }
  }
  return bestMoves[Math.floor(rand() * bestMoves.length)];
}
