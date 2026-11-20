// 앱의 GameLogic.kt와 같은 규칙. 서버에서 모든 수를 다시 검증합니다.

export type Side = "CAT" | "HAMSTER";
export type Sets = [number, number];
export interface Board {
  cat: Sets;
  ham: Sets;
  /** 남은 분할 횟수 */
  catSplits: number;
  hamSplits: number;
  /** 이번 판에서 쓸 수 있는 부활물약 수 */
  catRevives?: number;
  hamRevives?: number;
}

export type Move =
  | { kind: "add"; from: number; toOpponent: boolean; to: number }
  | { kind: "split"; from: number }
  | { kind: "revive"; index: number };

export const EXPLODE_AT = 5;
/** 한 게임에서 한 사람이 쓸 수 있는 분할 횟수 */
export const MAX_SPLITS = 3;

export const opponent = (s: Side): Side => (s === "CAT" ? "HAMSTER" : "CAT");
export const label = (s: Side) => (s === "CAT" ? "고양이" : "햄스터");
const setName = (i: number) => `${i + 1}번 세트`;
const setNameEn = (i: number) => `Set ${i + 1}`;
const labelEn = (s: Side) => (s === "CAT" ? "Cat" : "Hamster");

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
  if (p[0] === "R" && p.length === 2) {
    const index = idx(p[1]);
    return Number.isNaN(index) ? null : { kind: "revive", index };
  }
  if (p[0] === "S" && p.length === 2) {
    const from = idx(p[1]);
    return Number.isNaN(from) ? null : { kind: "split", from };
  }
  return null;
}

export function splitsLeft(b: Board, s: Side): number {
  return s === "CAT" ? b.catSplits : b.hamSplits;
}

export function canSplit(b: Board, side: Side, from: number): boolean {
  const mine = sets(b, side);
  const n = mine[from];
  return splitsLeft(b, side) > 0 && n > 0 && n % 2 === 0 && mine[1 - from] === 0;
}

export function revivesLeft(b: Board, s: Side): number {
  return (s === "CAT" ? b.catRevives : b.hamRevives) ?? 0;
}

export function canRevive(b: Board, side: Side, index: number): boolean {
  const mine = sets(b, side);
  return revivesLeft(b, side) > 0 && mine[index] === 0 && mine[1 - index] > 0;
}

export function isLegal(b: Board, side: Side, m: Move): boolean {
  if (winner(b) !== null) return false;
  const mine = sets(b, side);
  if (m.kind === "split") return canSplit(b, side, m.from);
  if (m.kind === "revive") return canRevive(b, side, m.index);
  if (mine[m.from] <= 0) return false;
  if (m.toOpponent) return sets(b, opponent(side))[m.to] > 0;
  return m.to !== m.from && mine[m.to] > 0;
}

export interface MoveResult {
  board: Board;
  exploded: { side: Side; index: number } | null;
  description: string;
  descriptionEn: string;
}

export function applyMove(b: Board, side: Side, m: Move): MoveResult {
  if (!isLegal(b, side, m)) throw new Error("illegal move");
  const who = label(side);
  const whoEn = labelEn(side);
  if (m.kind === "revive") {
    const v = [...sets(b, side)] as Sets;
    v[m.index] = 1;
    const left = revivesLeft(b, side) - 1;
    const revived = withSets(b, side, v);
    return {
      board: side === "CAT" ? { ...revived, catRevives: left } : { ...revived, hamRevives: left },
      exploded: null,
      description: `${who}: 💖 부활물약으로 ${setName(m.index)} 부활!`,
      descriptionEn: `${whoEn}: 💖 revived ${setNameEn(m.index)} with a potion!`,
    };
  }
  if (m.kind === "split") {
    const half = sets(b, side)[m.from] / 2;
    const left = splitsLeft(b, side) - 1;
    const split = withSets(b, side, [half, half]);
    return {
      board: side === "CAT" ? { ...split, catSplits: left } : { ...split, hamSplits: left },
      exploded: null,
      description: `${who}: ${setName(m.from)} 분할 → ${half}마리, ${half}마리 ✂️ (분할 -1, 남은 ${left}회)`,
      descriptionEn: `${whoEn}: split ${setNameEn(m.from)} → ${half}, ${half} ✂️ (split -1, ${left} left)`,
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
    descriptionEn:
      `${whoEn}: ${setNameEn(m.from)} (${power}) ${m.toOpponent ? "attacks their" : "boosts own"} ${setNameEn(m.to)}` +
      (boom ? " → 💥 POP!" : ` → ${sum}`),
  };
}
