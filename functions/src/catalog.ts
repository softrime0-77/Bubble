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

/** 무료 캐릭터 (모습만 바뀌고 효과 없음, 소모되지 않음) */
export const FREE_SKINS = ["HAMSTER"];

export function isFreeSkin(id: unknown): id is string {
  return typeof id === "string" && FREE_SKINS.includes(id);
}

/** 유료 스킨 (점수/상점 포인트로 한 번 사면 계속 씀). 모습만 바뀌고 효과 없음 */
export const COSMETIC_SKINS: Record<string, number> = {
  PINK_CAT: 300,
  BLACK_CAT: 300,
  SNOW_HAMSTER: 300,
  GOLD_HAMSTER: 500,
  // 💰 복냥이: 한 번 사면 계속. 공격할 때마다 50% 확률로 상점 포인트 +1 (하루 최대 30)
  LUCKY_CAT: 1000,
};

/** 복냥이 효과 */
export const LUCKY_SKIN = "LUCKY_CAT";
export const LUCKY_CHANCE = 0.5;
export const LUCKY_DAILY_MAX = 30;

/**
 * 복냥이 공격 한 번의 결과: 💰 +1을 받는지.
 * [todayCount]는 오늘 이미 받은 개수 (하루 최대를 넘으면 굴리지 않음), [rand]는 0 이상 1 미만.
 */
export function luckyRoll(todayCount: number, rand: number): boolean {
  return todayCount < LUCKY_DAILY_MAX && rand < LUCKY_CHANCE;
}

export function isCosmetic(id: unknown): id is string {
  return typeof id === "string" && COSMETIC_SKINS[id] !== undefined;
}

/** 장착한 캐릭터가 "모습만" 스킨이면 그 ID (무료 햄스터 또는 가진 유료 스킨), 아니면 null */
export function lookSkin(equipped: unknown, ownedSkins: unknown): string | null {
  if (isFreeSkin(equipped)) return equipped;
  if (isCosmetic(equipped) && Array.isArray(ownedSkins) && ownedSkins.includes(equipped)) return equipped;
  return null;
}

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
 * 흙(~1000) 0%, 구리(1001~1400) 5%, 은(1401~2000) 10%, 금(2001~4000) 15%,
 * 루비(4001~7000) 20%, 사파이어(7001~10000) 25%, 다이아(10001~50000) 30%, 핑크다이아(50001~) 35%.
 */
export const RANKS: { name: string; min: number; bonus: number }[] = [
  { name: "PINK_DIAMOND", min: 50001, bonus: 35 },
  { name: "DIAMOND", min: 10001, bonus: 30 },
  { name: "SAPPHIRE", min: 7001, bonus: 25 },
  { name: "RUBY", min: 4001, bonus: 20 },
  { name: "GOLD", min: 2001, bonus: 15 },
  { name: "SILVER", min: 1401, bonus: 10 },
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

/** 첫 구매 2배는 가입 후 7일 동안만 */
export const FIRST_BUY_WINDOW_MS = 7 * 86_400_000;
/** 7일 끝 무렵에 결제했는데 영수증 확인이 늦게 오는 경우를 위한 여유 (서버만) */
export const FIRST_BUY_GRACE_MS = 86_400_000;

/**
 * 이번 포인트 결제가 첫 구매 2배인지: 아직 포인트를 산 적 없고, 가입(createdAt, ms) 후 7일(+여유) 안.
 * 가입 시각을 모르는 옛 계정은 7일이 지난 것으로 봄.
 */
export function firstBuyBonus(firstBuyDone: unknown, createdAt: unknown, now: number): boolean {
  if (firstBuyDone === true) return false;
  if (typeof createdAt !== "number" || !Number.isFinite(createdAt)) return false;
  return now - createdAt < FIRST_BUY_WINDOW_MS + FIRST_BUY_GRACE_MS;
}
