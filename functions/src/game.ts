// 앱의 GameLogic.kt와 같은 규칙. 서버에서 모든 수를 다시 검증합니다.

export type Side = "CAT" | "HAMSTER";
export type Sets = [number, number];
export interface Board { cat: Sets; ham: Sets }

export type Move =
  | { kind: "add"; from: number; toOpponent: boolean; to: number }
  | { kind: "split"; from: number };

export const EXPLODE_AT = 5;

export const opponent = (s: Side): Side => (s === "CAT" ? "HAMSTER" : "CAT");
export const label = (s: Side) => (s === "CAT" ? "고양이" : "햄스터");
const setName = (i: number) => `${i + 1}번 세트`;

export function sets(b: Board, s: Side): Sets {
  return s === "CAT" ? b.cat : b.ham;
}

function withSets(b: Board, s: Side, v: Sets): Board {
  return s === "CAT" ? { ...b, cat: v } : { ...b, ham: v };
}

export function winner(b: Board): Side | null {
  if (b.cat[0] === 0 && b.cat[1] === 0) return "HAMSTER";
  if (b.ham[0] === 0 && b.ham[1] === 0) return "CAT";
  return null;
}

/** 앱이 보내는 문자열 형식: "A:from:toOpponent(0/1):to" 또는 "S:from" */
export function decodeMove(s: unknown): Move | null {
  if (typeof s !== "string") return null;
  const p = s.split(":");
  const idx = (x: string) => (x === "0" || x === "1" ? Number(x) : NaN);
  if (p[0] === "A" && p.length === 4) {
    const from = idx(p[1]);
    const to = idx(p[3]);
    if (Number.isNaN(from) || Number.isNaN(to) || (p[2] !== "0" && p[2] !== "1")) return null;
    return { kind: "add", from, toOpponent: p[2] === "1", to };
  }
  if (p[0] === "S" && p.length === 2) {
    const from = idx(p[1]);
    return Number.isNaN(from) ? null : { kind: "split", from };
  }
  return null;
}

export function canSplit(b: Board, side: Side, from: number): boolean {
  const mine = sets(b, side);
  const n = mine[from];
  return n > 0 && n % 2 === 0 && mine[1 - from] === 0;
}

export function isLegal(b: Board, side: Side, m: Move): boolean {
  if (winner(b) !== null) return false;
  const mine = sets(b, side);
  if (m.kind === "split") return canSplit(b, side, m.from);
  if (mine[m.from] <= 0) return false;
  if (m.toOpponent) return sets(b, opponent(side))[m.to] > 0;
  return m.to !== m.from && mine[m.to] > 0;
}

export interface MoveResult {
  board: Board;
  exploded: { side: Side; index: number } | null;
  description: string;
}

export function applyMove(b: Board, side: Side, m: Move): MoveResult {
  if (!isLegal(b, side, m)) throw new Error("illegal move");
  const who = label(side);
  if (m.kind === "split") {
    const half = sets(b, side)[m.from] / 2;
    return {
      board: withSets(b, side, [half, half]),
      exploded: null,
      description: `${who}: ${setName(m.from)} 분할 → ${half}마리, ${half}마리 ✂️`,
    };
  }
  const power = sets(b, side)[m.from];
  const targetSide = m.toOpponent ? opponent(side) : side;
  const target = [...sets(b, targetSide)] as Sets;
  const sum = target[m.to] + power;
  const boom = sum >= EXPLODE_AT;
  target[m.to] = boom ? 0 : sum;
  const targetName = (m.toOpponent ? "상대 " : "내 ") + setName(m.to);
  const verb = m.toOpponent ? "공격" : "강화";
  return {
    board: withSets(b, targetSide, target),
    exploded: boom ? { side: targetSide, index: m.to } : null,
    description: `${who}: ${setName(m.from)}(${power})로 ${targetName} ${verb}` + (boom ? " → 💥 펑!" : ` → ${sum}마리`),
  };
}
