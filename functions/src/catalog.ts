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
 * 계급 보너스: 이겨서 얻는 점수에 추가로 붙는 비율(%).
 * 흙(1000점 이하) 0%, 구리(1001~) 5%, 은(1101~) 10%, 금(1201~) 15%, 다이아(1301~) 20%.
 */
export function rankBonusPercent(score: number): number {
  if (score >= 1301) return 20;
  if (score >= 1201) return 15;
  if (score >= 1101) return 10;
  if (score >= 1001) return 5;
  return 0;
}

/** 얻는 점수 [base]에 붙는 보너스 점수 (반올림). */
export function rankBonus(base: number, score: number): number {
  return base > 0 ? Math.round((base * rankBonusPercent(score)) / 100) : 0;
}
