// 상점 상품과 계급. 앱의 game/Catalog.kt와 같은 값이어야 합니다.

export const POTION_PRICE = 50;
/** ×3 점수 부스터 (소모품): 켜둔 상태로 랜덤 매칭이 시작되면 1개 사용, 이기면 얻는 점수 3배 (AI 대전엔 안 씀) */
export const BOOST_PRICE = 50;
export const BOOST_MULTIPLIER = 3;

/** 이겼을 때 얻는 점수: (기본 + 계급 보너스) × 부스터 */
export function winPoints(base: number, bonus: number, boosted: boolean): number {
  return (base + bonus) * (boosted ? BOOST_MULTIPLIER : 1);
}
/** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
export const MAX_REVIVES_PER_GAME = 3;

/** 이번 판에서 쓸 수 있는 부활 수 = 가진 물약 수 (최대 3). */
export function revivesForGame(potions: unknown): number {
  const n = typeof potions === "number" && potions > 0 ? Math.floor(potions) : 0;
  return Math.min(MAX_REVIVES_PER_GAME, n);
}

/** 동물 캐릭터 (소모품): 장착한 채 랜덤 매칭이 잡히면 1개를 쓰고, 그 판에 상대의 턴 시간을 줄입니다. */
export const ANIMALS: Record<string, { price: number; opponentTurnMs: number }> = {
  SHEEP: { price: 30, opponentTurnMs: 15_000 },
  DOG: { price: 50, opponentTurnMs: 10_000 },
  GIRAFFE: { price: 100, opponentTurnMs: 5_000 },
};

/** 기본 턴 시간 (상대가 동물을 쓰면 더 짧아짐) */
export const DEFAULT_TURN_MS = 20_000;

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

/**
 * 상점 결제 나누기: 상점 포인트(coins)를 먼저 쓰고 모자라면 점수(score)에서.
 * 둘을 합쳐도 모자라면 null.
 */
export function splitPayment(price: number, coins: number, score: number): { fromCoins: number; fromScore: number } | null {
  const fromCoins = Math.min(Math.max(coins, 0), price);
  const fromScore = price - fromCoins;
  return fromScore > Math.max(score, 0) ? null : { fromCoins, fromScore };
}

/** 국가 코드 정리: 영문 대문자 2글자만 (아니면 null) */
export function cleanCountry(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const c = raw.trim().toUpperCase();
  return /^[A-Z]{2}$/.test(c) ? c : null;
}
