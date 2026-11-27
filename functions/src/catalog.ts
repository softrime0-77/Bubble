// 상점 상품과 계급. 앱의 game/Catalog.kt와 같은 값이어야 합니다.

export const POTION_PRICE = 50;
/** 관리자가 넣을 수 있는 값 범위 (실수로 엄청 큰 값을 넣지 않게) */
export const ADMIN_MAX_VALUE = 10_000_000;

/** 관리자 수정 값 확인: 0 이상 정수만, 없으면 undefined(안 바꿈), 잘못되면 null */
export function adminValue(raw: unknown): number | undefined | null {
  if (raw === undefined || raw === null) return undefined;
  if (typeof raw !== "number" || !Number.isInteger(raw) || raw < 0 || raw > ADMIN_MAX_VALUE) return null;
  return raw;
}

/** ✏️ 닉네임 변경권: 첫 변경은 무료, 그다음부터 1장씩 */
export const RENAME_PRICE = 100;

/** 닉네임 정리: 앞뒤 공백 제거, 최대 12자. 비었으면 null */
export function cleanName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const n = [...raw.trim()].slice(0, 12).join("");
  return n.length > 0 ? n : null;
}

/** 이번 닉네임 변경이 무료인지 / 변경권을 쓰는지 / 못 바꾸는지 */
export function renameCost(freeUsed: unknown, tickets: unknown): "free" | "ticket" | "none" {
  if (freeUsed !== true) return "free";
  return typeof tickets === "number" && tickets >= 1 ? "ticket" : "none";
}
/**
 * 🚩 땅: 고양이 왕국에서 독립하는 최종 목표. 한 번만 살 수 있고,
 * 결제로 충전한 💰 상점 포인트는 못 쓰고 게임으로 번 점수(score)로만 삽니다.
 */
export const LAND_PRICE = 100_000;

/** 땅을 살 수 있는지: 이미 있으면 "owned", 점수가 모자라면 "short" */
export function landCheck(owned: unknown, score: number): "ok" | "owned" | "short" {
  if (owned === true) return "owned";
  return score >= LAND_PRICE ? "ok" : "short";
}

/** ×3 점수 부스터 (소모품): 켜둔 상태로 랜덤 매칭이 시작되면 1개 사용, 이기면 얻는 점수 3배 (AI 대전엔 안 씀) */
export const BOOST_PRICE = 100;
export const BOOST_MULTIPLIER = 3;

/** 이겼을 때 얻는 점수: (기본 + 계급 보너스) × 부스터 */
export function winPoints(base: number, bonus: number, boosted: boolean): number {
  return (base + bonus) * (boosted ? BOOST_MULTIPLIER : 1);
}
/** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
export const MAX_REVIVES_PER_GAME = 3;

/** 랜덤 매칭(랭킹)에서는 한 판에 부활 1번까지 (물약을 쌓아 둔 사람만 이기지 않게) */
export const RANKED_MAX_REVIVES = 1;

/** 이번 판에서 쓸 수 있는 부활 수 = 가진 물약 수 (최대 [max], 기본 3). */
export function revivesForGame(potions: unknown, max = MAX_REVIVES_PER_GAME): number {
  const n = typeof potions === "number" && potions > 0 ? Math.floor(potions) : 0;
  return Math.min(max, n);
}

/** 동물 캐릭터 (소모품): 장착한 채 랜덤 매칭이 잡히면 1개를 쓰고, 그 판에 상대의 턴 시간을 줄입니다. */
export const ANIMALS: Record<string, { price: number; opponentTurnMs: number }> = {
  SHEEP: { price: 20, opponentTurnMs: 15_000 },
  DOG: { price: 40, opponentTurnMs: 12_000 },
  // 5초는 너무 빡빡해서 8초로 (손이 느려 지는 판이 없게)
  GIRAFFE: { price: 70, opponentTurnMs: 8_000 },
};

/**
 * 묶음 할인: 상품 → { 개수: 가격 }. 부활물약은 1·3·5·10개, 동물은 1·3개.
 * (물약 3개 10% · 5개 20% · 10개 30% 할인, 동물 3개 약 15% 할인)
 */
export const BUNDLES: Record<string, Record<number, number>> = {
  POTION: { 1: 50, 3: 135, 5: 200, 10: 350 },
  SHEEP: { 1: 20, 3: 50 },
  DOG: { 1: 40, 3: 100 },
  GIRAFFE: { 1: 70, 3: 180 },
};

/** [item]을 [qty]개 살 때 가격. 없는 묶음이면 null */
export function bundlePrice(item: string, qty: unknown): number | null {
  const table = BUNDLES[item];
  if (!table || typeof qty !== "number" || !Number.isInteger(qty)) return null;
  return table[qty] ?? null;
}

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
  // 🐙 문어 · 🐧 펭귄: 모습 + 분할/공격 효과만
  OCTOPUS: 400,
  PENGUIN: 400,
  // 🐼 출석 판다: 장착하고 출석하면 💰 +10
  PANDA: 500,
  // 🐉 아기 용: 상대 방울을 터뜨리면 💰 +2 (하루 최대 10)
  DRAGON: 700,
  // 🐰 연승 토끼: 연승할수록 리본이 늘어남 (최대 5개)
  RABBIT: 600,
};

/** 계급 캐릭터: 팔지 않고, 점수가 이 이상이면 장착 가능 (🦄 유니콘 = 다이아 이상) */
export const RANK_SKINS: Record<string, number> = { UNICORN: 10001 };

export function isRankSkin(id: unknown): id is string {
  return typeof id === "string" && RANK_SKINS[id] !== undefined;
}

/** 🐼 출석 판다: 출석 보너스 */
export const PANDA_SKIN = "PANDA";
export const PANDA_CHECKIN_BONUS = 10;

/** 🐉 아기 용: 상대 방울을 터뜨릴 때 💰, 하루 최대 */
export const DRAGON_SKIN = "DRAGON";
export const DRAGON_POP_COINS = 2;
export const DRAGON_DAILY_MAX = 10;

/** 🐰 연승 토끼: 리본 최대 개수 */
export const RABBIT_SKIN = "RABBIT";
export const RABBIT_MAX_RIBBONS = 5;
/** 🐰 연승 보상 하루 최대 💰 */
export const RABBIT_DAILY_MAX = 30;

/**
 * 🐰 연승 토끼가 이겨서 [streak]연승이 됐을 때 받을 💰.
 * 3연승 5 · 5연승 15 · 6연승부터 이길 때마다 2. 오늘 이미 받은 [todayCoins]까지 합쳐 하루 최대 30
 * (10연승이면 5 + 15 + 2×5 = 30). 지면 연승이 처음부터라 다시 3연승하면 또 5.
 */
export function rabbitCoins(streak: number, todayCoins: number): number {
  const base = streak === 3 ? 5 : streak === 5 ? 15 : streak > 5 ? 2 : 0;
  return Math.max(0, Math.min(base, RABBIT_DAILY_MAX - todayCoins));
}

/** 이번에 아기 용이 받을 💰 (오늘 이미 받은 [todayCoins] 기준, 하루 최대를 넘지 않게) */
export function dragonCoins(todayCoins: number): number {
  return Math.max(0, Math.min(DRAGON_POP_COINS, DRAGON_DAILY_MAX - todayCoins));
}

/** 🎓 튜토리얼 완료 보상 (계정당 한 번) */
export const TUTORIAL_COINS = 20;
export const TUTORIAL_POTIONS = 1;

/** 🦄 유니콘 무지개 축복: 이길 때마다 💰, 하루 최대 */
export const UNICORN_SKIN = "UNICORN";
export const UNICORN_WIN_COINS = 3;
export const UNICORN_DAILY_MAX = 30;

/** 이번 승리로 유니콘이 받을 💰 (오늘 이미 받은 [todayCoins] 기준, 하루 최대를 넘지 않게) */
export function unicornCoins(todayCoins: number): number {
  return Math.max(0, Math.min(UNICORN_WIN_COINS, UNICORN_DAILY_MAX - todayCoins));
}

/** 장착한 판다를 실제로 가졌는지 */
export function pandaEquipped(equipped: unknown, ownedSkins: unknown): boolean {
  return equipped === PANDA_SKIN && Array.isArray(ownedSkins) && ownedSkins.includes(PANDA_SKIN);
}

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

/** 장착한 캐릭터가 "모습" 스킨이면 그 ID (무료 햄스터 · 가진 유료 스킨 · 계급이 되는 계급 캐릭터), 아니면 null */
export function lookSkin(equipped: unknown, ownedSkins: unknown, score = 0): string | null {
  if (isFreeSkin(equipped)) return equipped;
  if (isRankSkin(equipped) && score >= RANK_SKINS[equipped]) return equipped;
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
