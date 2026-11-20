// 상점 상품과 계급. 앱의 game/Catalog.kt와 같은 값이어야 합니다.

export const POTION_PRICE = 1000;
/** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
export const MAX_REVIVES_PER_GAME = 3;

/** 이번 판에서 쓸 수 있는 부활 수 = 가진 물약 수 (최대 3). */
export function revivesForGame(potions: unknown): number {
  const n = typeof potions === "number" && potions > 0 ? Math.floor(potions) : 0;
  return Math.min(MAX_REVIVES_PER_GAME, n);
}

/** 동물 캐릭터: 장착하면 상대의 턴 제한 시간이 줄어듭니다. */
export const ANIMALS: Record<string, { price: number; opponentTurnMs: number }> = {
  SHEEP: { price: 500, opponentTurnMs: 20_000 },
  DOG: { price: 750, opponentTurnMs: 15_000 },
  GIRAFFE: { price: 1000, opponentTurnMs: 10_000 },
};

export const DEFAULT_TURN_MS = 30_000;

/** 상대가 장착한 동물에 따라 정해지는 내 턴 제한 시간. */
export function turnLimitAgainst(opponentEquipped: unknown): number {
  return typeof opponentEquipped === "string" && ANIMALS[opponentEquipped]
    ? ANIMALS[opponentEquipped].opponentTurnMs
    : DEFAULT_TURN_MS;
}

/**
 * 계급과 보너스. 앱의 game/Catalog.kt Rank와 같아야 합니다.
 * 흙(~1000) 0%, 구리(1001~) 5%, 은(1500~) 10%, 금(2000~) 15%,
 * 루비(5000~) 20%, 사파이어(7000~) 25%, 다이아(10000~) 30%.
 */
export const RANKS: { name: string; min: number; bonus: number }[] = [
  { name: "DIAMOND", min: 10000, bonus: 30 },
  { name: "SAPPHIRE", min: 7000, bonus: 25 },
  { name: "RUBY", min: 5000, bonus: 20 },
  { name: "GOLD", min: 2000, bonus: 15 },
  { name: "SILVER", min: 1500, bonus: 10 },
  { name: "COPPER", min: 1001, bonus: 5 },
];

export function rankBonusPercent(score: number): number {
  return RANKS.find((r) => score >= r.min)?.bonus ?? 0;
}

/** 얻는 점수 [base]에 붙는 보너스 점수 (반올림). */
export function rankBonus(base: number, score: number): number {
  return base > 0 ? Math.round((base * rankBonusPercent(score)) / 100) : 0;
}
