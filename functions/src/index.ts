// Bubble 냥햄대전 서버.
// 매칭, 수 두기, 승패 판정, 점수 계산을 전부 서버에서 처리합니다.
// 앱은 games/queue 문서를 직접 쓸 수 없고(firestore.rules), 이 함수들을 호출만 합니다.

import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { DocumentReference, DocumentSnapshot, FieldPath, FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { CallableRequest, HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { googleKeyLookup, verifySsv } from "./ssv";
import { appAccountToken, verifyAppleJws } from "./apple";
import { normalizeCode, randomCode } from "./referral";
import { pickOpponent } from "./matching";
import { GoogleAuth } from "google-auth-library";
import { createHash } from "node:crypto";
import { AiLevel, chooseMove, isAiLevel } from "./ai";
import { ANIMALS, BOOST_PRICE, cleanCountry, COSMETIC_SKINS, isCosmetic, isFreeSkin, lookSkin, DEFAULT_TURN_MS, POTION_PRICE, winPoints, rankBonus, revivesForGame, splitPayment, turnLimitAgainst, firstBuyBonus, LUCKY_SKIN, luckyRoll, isRankSkin, RANK_SKINS, PANDA_CHECKIN_BONUS, pandaEquipped, DRAGON_SKIN, dragonCoins, RABBIT_MAX_RIBBONS, RABBIT_SKIN, rabbitCoins, UNICORN_SKIN, unicornCoins, TUTORIAL_COINS, TUTORIAL_POTIONS, RANKED_MAX_REVIVES, BUNDLES, bundlePrice, RENAME_PRICE, cleanName, renameCost, adminValue, LAND_PRICE, landCheck } from "./catalog";
import { CHECKIN_COINS, CHECKIN_POTION_DAY, MISSIONS, missionProgress, nextStreak, POTION_ADS_PER_DAY, potionAdsToday, weekKey, weeklyCoins } from "./rewards";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { dayKey, friendDeltas, isRated, pairKey, roomJoinable, FRIEND_ROOM_TTL_MS } from "./friend";
import { applyMove, Board, decodeMove, encodeMove, isLegal, MAX_SPLITS, opponent, Side, winner } from "./game";

initializeApp();
const db = getFirestore();

/** 오류: message는 한국어, details.en은 영어 (앱이 화면 언어에 맞춰 고름) */
function err(code: ConstructorParameters<typeof HttpsError>[0], ko: string, en: string): HttpsError {
  return new HttpsError(code, ko, { en });
}

setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });

/** 서버 버전 (로그 확인용). 바꾸면 모든 함수가 다시 배포되어 공개 호출 권한도 다시 설정됨 */
const SERVER_VERSION = "2026-11-22";
logger.debug("server version", { SERVER_VERSION });

const START_SCORE = 1000;
const WIN_POINTS = 30;
const LOSE_POINTS = 10;
const TURN_LIMIT_MS = DEFAULT_TURN_MS;
/** 턴 시간 판정 여유 (네트워크 지연용). 시간 판단은 서버 시계로만 함 */
const TIMEOUT_GRACE_MS = 2_000;
const QUEUE_STALE_MS = 15_000;
/** 패배 후 보상형 광고를 보면 돌려주는 점수 (잃은 점수가 이보다 적으면 잃은 만큼만) */
const AD_REFUND_POINTS = 8;
/** AI 대전 점수 (난이도별 승리/패배). 승패 기록(wins/losses)에는 넣지 않음. */
const AI_POINTS: Record<AiLevel, { win: number; lose: number }> = {
  EASY: { win: 1, lose: 0 },
  NORMAL: { win: 5, lose: -1 },
  HARD: { win: 10, lose: -2 },
};
const AI_UID = "AI";
/** AI 대전에서 사람 쪽 턴 시간 (난이도별) */
const AI_TURN_MS: Record<AiLevel, number> = { EASY: 20_000, NORMAL: 15_000, HARD: 8_000 };
const AI_LABEL: Record<AiLevel, string> = { EASY: "쉬움", NORMAL: "보통", HARD: "어려움" };
const AI_THINK_MS = 900;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * 우리 보상형 전면 광고 단위 번호. 테스트 광고 단위는 거절.
 * 안드로이드: ca-app-pub-…/6715010091, 아이폰: ca-app-pub-…/9985608059 (보상형 전면).
 */
const REWARDED_AD_UNITS = ["6715010091", "9985608059"];

/** 구글로 로그인한 사용자만 허용합니다. */
function requireUser(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw err("unauthenticated", "로그인이 필요해요", "Please sign in");
  const provider = req.auth?.token.firebase?.sign_in_provider;
  if (provider !== "google.com" && provider !== "apple.com") {
    throw err("permission-denied", "구글 또는 Apple 계정으로 로그인해 주세요", "Please sign in with Google or Apple");
  }
  return uid;
}

function requireGameId(data: unknown): string {
  const id = (data as { gameId?: unknown } | null)?.gameId;
  if (typeof id !== "string" || !/^[A-Za-z0-9]{1,40}$/.test(id)) {
    throw err("invalid-argument", "잘못된 게임이에요", "Invalid game");
  }
  return id;
}

interface Game {
  catUid: string;
  hamUid: string;
  cat: [number, number];
  ham: [number, number];
  /** 남은 분할 횟수 (예전 게임 문서엔 없을 수 있음) */
  catSplits?: number;
  hamSplits?: number;
  catRevives?: number;
  hamRevives?: number;
  /** 각자 자기 차례에 쓸 수 있는 시간 (상대가 장착한 동물에 따라 줄어듦) */
  catTurnMs?: number;
  hamTurnMs?: number;
  turn: Side;
  status: "playing" | "finished";
  /** AI 대전이면 난이도 */
  vsAi?: AiLevel;
  /** 친구 대전 (초대 링크) */
  vsFriend?: boolean;
  /** 친구 대전에서 점수가 걸린 판인지 (같은 친구와 하루 5판까지) */
  rated?: boolean;
  /** 이번 판에 ✖️3 부스터를 쓴 사람 */
  catBoost?: boolean;
  hamBoost?: boolean;
  /** 이번 판에 장착한 캐릭터 (모습, 복냥이 효과 판정에도 씀) */
  catSkin?: string | null;
  hamSkin?: string | null;
  /** 🐰 시작할 때의 연승 리본 수 */
  catStreak?: number;
  hamStreak?: number;
  moveNo: number;
  turnStartedAt: number;
}

function sideOf(g: Game, uid: string): Side | null {
  if (uid === g.catUid) return "CAT";
  if (uid === g.hamUid) return "HAMSTER";
  return null;
}

async function loadGame(tx: Transaction, gameId: string, uid: string) {
  const ref = db.collection("games").doc(gameId);
  const snap = await tx.get(ref);
  if (!snap.exists) throw err("not-found", "게임을 찾을 수 없어요", "Game not found");
  const game = snap.data() as Game;
  const side = sideOf(game, uid);
  if (!side) throw err("permission-denied", "내 게임이 아니에요", "This is not your game");
  return { ref, game, side };
}

/**
 * 🐰 연승 토끼 보상: 이 판을 토끼로 이겨서 새 연승이 3·5·6+가 되면 💰.
 * 이긴 사람 문서에 넣을 필드와 게임 문서에 남길 값(앱 결과 화면용)을 돌려줌.
 */
function rabbitWin(winner: DocumentSnapshot, skin: string | null | undefined) {
  if (skin !== RABBIT_SKIN) return { user: {}, coins: 0, streak: 0 };
  const streak = ((winner.get("winStreak") as number | undefined) ?? 0) + 1;
  const today = dayKey(Date.now());
  const got = winner.get("rabbitDay") === today ? ((winner.get("rabbitCoins") as number | undefined) ?? 0) : 0;
  const coins = rabbitCoins(streak, got);
  if (coins <= 0) return { user: {}, coins: 0, streak };
  return { user: { rabbitDay: today, rabbitCoins: got + coins }, coins, streak };
}

/** 🦄 유니콘 무지개 축복: 이 판을 유니콘으로 이기면 💰 +3 (하루 최대 30) */
function unicornWin(winner: DocumentSnapshot, skin: string | null | undefined) {
  if (skin !== UNICORN_SKIN) return { user: {}, coins: 0 };
  const today = dayKey(Date.now());
  const got = winner.get("unicornDay") === today ? ((winner.get("unicornCoins") as number | undefined) ?? 0) : 0;
  const coins = unicornCoins(got);
  if (coins <= 0) return { user: {}, coins: 0 };
  return { user: { unicornDay: today, unicornCoins: got + coins }, coins };
}

/** 이긴 사람 보상(토끼·유니콘)을 합침: 💰은 한 번에 더함 */
function winRewards(winner: DocumentSnapshot, skin: string | null | undefined) {
  const r = rabbitWin(winner, skin);
  const u = unicornWin(winner, skin);
  const coins = r.coins + u.coins;
  const user: Record<string, unknown> = { ...r.user, ...u.user };
  if (coins > 0) user.coins = FieldValue.increment(coins);
  return { user, rabbitCoins: r.coins, rabbitStreak: r.streak, unicornCoins: u.coins };
}

/**
 * 게임을 끝내고 두 사람의 점수를 반영합니다.
 * 트랜잭션 규칙상 읽기를 먼저 모두 끝낸 다음 호출해야 하므로, 사용자 문서는 미리 읽어 둡니다.
 */
function finishGame(
  tx: Transaction,
  gameRef: DocumentReference,
  game: Game,
  win: Side,
  reason: "normal" | "forfeit" | "timeout",
  users: { cat: DocumentSnapshot; ham: DocumentSnapshot },
  extra: Record<string, unknown> = {},
) {
  const score = (s: DocumentSnapshot) => (s.get("score") as number | undefined) ?? START_SCORE;
  const catWon = win === "CAT";
  if (game.vsAi) {
    // AI 대전: 사람 쪽 점수만 난이도표대로 (0점 밑으로는 안 내려감)
    const human: Side = game.catUid === AI_UID ? "HAMSTER" : "CAT";
    const humanSnap = human === "CAT" ? users.cat : users.ham;
    const pts = AI_POINTS[game.vsAi];
    const bonus = win === human ? rankBonus(pts.win, score(humanSnap)) : 0;
    const boosted = (human === "CAT" ? game.catBoost : game.hamBoost) === true;
    const delta = win === human ? winPoints(pts.win, bonus, boosted) : -Math.min(-pts.lose, score(humanSnap));
    // 🐰 연승 토끼: AI 대전도 연승(리본)과 연승 보상에 들어감 (하루 최대가 있어서 괜찮음)
    const humanWon = win === human;
    const rabbit = humanWon ? rabbitWin(humanSnap, human === "CAT" ? game.catSkin : game.hamSkin) : { user: {}, coins: 0, streak: 0 };
    tx.update(gameRef, {
      ...extra, status: "finished", winner: win, reason,
      [human === "CAT" ? "catDelta" : "hamDelta"]: delta,
      [human === "CAT" ? "catBonus" : "hamBonus"]: bonus,
      rabbitCoins: rabbit.coins, rabbitStreak: rabbit.streak,
    });
    // 일일 미션: AI 대전도 "대전 하기"·"이기기"에 들어감
    tx.update(humanSnap.ref, {
      ...missionProgress(humanSnap.data() ?? {}, dayKey(Date.now()), { play: 1, win: humanWon ? 1 : 0, random: 0 }),
      ...(delta !== 0 ? { score: FieldValue.increment(delta) } : {}),
      winStreak: humanWon ? FieldValue.increment(1) : 0,
      ...rabbit.user,
      ...(rabbit.coins > 0 ? { coins: FieldValue.increment(rabbit.coins) } : {}),
    });
    return;
  }
  if (game.vsFriend) {
    // 친구 대전: 이기면 +30, 지면 -10 (계급 보너스·부스터 없음). 하루 제한을 넘긴 판은 점수 없음
    const loserSnap = catWon ? users.ham : users.cat;
    const d = friendDeltas(game.rated === true, score(loserSnap));
    const catDelta = catWon ? d.win : d.lose;
    const hamDelta = catWon ? d.lose : d.win;
    const rabbit = game.rated
      ? winRewards(catWon ? users.cat : users.ham, catWon ? game.catSkin : game.hamSkin)
      : { user: {}, rabbitCoins: 0, rabbitStreak: 0, unicornCoins: 0 };
    tx.update(gameRef, {
      ...extra, status: "finished", winner: win, reason, catDelta, hamDelta, catBonus: 0, hamBonus: 0,
      rabbitCoins: rabbit.rabbitCoins, rabbitStreak: rabbit.rabbitStreak, unicornCoins: rabbit.unicornCoins,
    });
    if (game.rated) {
      // 점수 걸린 친구 대전만 미션에 들어감 (친선전으로 미션 채우기 방지)
      const today = dayKey(Date.now());
      tx.update(users.cat.ref, {
        ...missionProgress(users.cat.data() ?? {}, today, { play: 1, win: catWon ? 1 : 0, random: 0 }),
        score: FieldValue.increment(catDelta), [catWon ? "wins" : "losses"]: FieldValue.increment(1),
        winStreak: catWon ? FieldValue.increment(1) : 0,
        ...(catWon ? rabbit.user : {}),
      });
      tx.update(users.ham.ref, {
        ...missionProgress(users.ham.data() ?? {}, today, { play: 1, win: catWon ? 0 : 1, random: 0 }),
        score: FieldValue.increment(hamDelta), [catWon ? "losses" : "wins"]: FieldValue.increment(1),
        winStreak: catWon ? 0 : FieldValue.increment(1),
        ...(catWon ? {} : rabbit.user),
      });
    }
    return;
  }
  // 이긴 쪽은 계급 보너스(구리 5% ~ 다이아 20%)를 더 받음
  const catBonus = catWon ? rankBonus(WIN_POINTS, score(users.cat)) : 0;
  const hamBonus = catWon ? 0 : rankBonus(WIN_POINTS, score(users.ham));
  const catDelta = catWon ? winPoints(WIN_POINTS, catBonus, game.catBoost === true) : -Math.min(LOSE_POINTS, score(users.cat));
  const hamDelta = catWon ? -Math.min(LOSE_POINTS, score(users.ham)) : winPoints(WIN_POINTS, hamBonus, game.hamBoost === true);
  // 🐰 연승 토끼 · 🦄 유니콘 보상 (이긴 쪽만)
  const rabbit = winRewards(catWon ? users.cat : users.ham, catWon ? game.catSkin : game.hamSkin);
  tx.update(gameRef, {
    ...extra, status: "finished", winner: win, reason, catDelta, hamDelta, catBonus, hamBonus,
    rabbitCoins: rabbit.rabbitCoins, rabbitStreak: rabbit.rabbitStreak, unicornCoins: rabbit.unicornCoins,
  });
  const today = dayKey(Date.now());
  tx.update(users.cat.ref, {
    ...missionProgress(users.cat.data() ?? {}, today, { play: 1, win: catWon ? 1 : 0, random: 1 }),
    score: FieldValue.increment(catDelta),
    [catWon ? "wins" : "losses"]: FieldValue.increment(1),
    winStreak: catWon ? FieldValue.increment(1) : 0,
    ...(catWon ? { ...rabbit.user, ...boostSpend(users.cat, game.catBoost === true) } : {}),
  });
  tx.update(users.ham.ref, {
    ...missionProgress(users.ham.data() ?? {}, today, { play: 1, win: catWon ? 0 : 1, random: 1 }),
    score: FieldValue.increment(hamDelta),
    [catWon ? "losses" : "wins"]: FieldValue.increment(1),
    winStreak: catWon ? 0 : FieldValue.increment(1),
    ...(catWon ? {} : { ...rabbit.user, ...boostSpend(users.ham, game.hamBoost === true) }),
  });
}

/** 🐰 연승 토끼 리본 수 (연승 수, 최대 5). 토끼가 아니어도 기록해 두고 앱이 토끼일 때만 그림 */
function ribbons(profile: DocumentSnapshot): number {
  const s = (profile.get("winStreak") as number | undefined) ?? 0;
  return Math.max(0, Math.min(RABBIT_MAX_RIBBONS, Math.floor(s)));
}

/** 부스터를 켜뒀고 남아 있으면 1개 쓰고 true. 다 쓰면 자동으로 끔. */
/**
 * 🚀 ×3 부스터를 이번 판에 걸지. 매칭 때는 쓰지 않고(개수 그대로), 이겼을 때만 1개 씀 (boostSpend).
 * 켜 뒀는데 남은 게 없으면 꺼 둠.
 */
function useBoost(tx: Transaction, profile: DocumentSnapshot): boolean {
  if (profile.get("boostOn") !== true) return false;
  const left = (profile.get("boosts") as number | undefined) ?? 0;
  if (left <= 0) {
    tx.update(profile.ref, { boostOn: false });
    return false;
  }
  return true;
}

/** 부스터를 건 판을 이겼으면 1개 씀 (지면 그대로 남음). 이긴 사람 문서에 더할 필드 */
function boostSpend(winner: DocumentSnapshot, used: boolean): Record<string, unknown> {
  if (!used) return {};
  const left = (winner.get("boosts") as number | undefined) ?? 0;
  return left - 1 > 0 ? { boosts: FieldValue.increment(-1) } : { boosts: 0, boostOn: false };
}

/** 지금 차례인 사람이 제한 시간을 넘겼는지 (서버 시계 기준) */
function isLate(game: Game): boolean {
  const limit = (game.turn === "CAT" ? game.catTurnMs : game.hamTurnMs) ?? TURN_LIMIT_MS;
  return Date.now() - game.turnStartedAt > limit + TIMEOUT_GRACE_MS;
}

/** 장착한 동물이 남아 있으면 1개를 쓰고 그 동물을, 없으면 null (장착도 해제). */
function useAnimal(tx: Transaction, profile: DocumentSnapshot): string | null {
  const equipped = profile.get("equipped") as string | null | undefined;
  // 무료 캐릭터(햄스터)는 모습만 바뀌고 소모되지 않음 (상대 턴 시간도 그대로)
  // 무료 햄스터·유료 스킨은 모습만 바뀌고 소모되지 않음 (상대 턴 시간도 그대로)
  const look = lookSkin(equipped, profile.get("skins"), (profile.get("score") as number | undefined) ?? START_SCORE);
  if (look) return look;
  if (!equipped || !ANIMALS[equipped]) return null;
  const left = (profile.get(`animals.${equipped}`) as number | undefined) ?? 0;
  if (left <= 0) {
    tx.update(profile.ref, { equipped: null });
    return null;
  }
  tx.update(profile.ref, left - 1 > 0
    ? { [`animals.${equipped}`]: FieldValue.increment(-1) }
    : { [`animals.${equipped}`]: 0, equipped: null });
  return equipped;
}

async function readPlayers(tx: Transaction, game: Game) {
  // AI 쪽은 사용자 문서가 없으므로, 사람 문서를 두 자리에 모두 넣어 둠 (finishGame이 사람 쪽만 씀)
  if (game.vsAi) {
    const human = await tx.get(db.collection("users").doc(game.catUid === AI_UID ? game.hamUid : game.catUid));
    return { cat: human, ham: human };
  }
  const [cat, ham] = await Promise.all([
    tx.get(db.collection("users").doc(game.catUid)),
    tx.get(db.collection("users").doc(game.hamUid)),
  ]);
  return { cat, ham };
}

/**
 * 랜덤 매칭. 앱은 상대를 찾을 때까지 몇 초마다 이 함수를 호출합니다(호출 자체가 "아직 기다리는 중" 신호).
 * 기다리는 다른 사람이 있으면 게임을 만들고 gameId를, 없으면 null을 돌려줍니다.
 */
export const matchmake = onCall(async (req) => {
  const uid = requireUser(req);
  const myCountry = cleanCountry((req.data as { country?: unknown } | null)?.country);
  return db.runTransaction(async (tx) => {
    const now = Date.now();
    const meRef = db.collection("queue").doc(uid);
    const me = await tx.get(meRef);

    // 이미 매칭됐고 그 게임이 아직 진행 중이면 그대로 돌려줌 (재접속)
    if (me.exists && me.get("status") === "matched") {
      const g = await tx.get(db.collection("games").doc(me.get("gameId")));
      if (g.exists && g.get("status") === "playing") return { gameId: g.id };
    }

    const myProfile = await tx.get(db.collection("users").doc(uid));
    if (!myProfile.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    // 랭킹에 국기를 보여주려고 나라를 프로필에도 저장
    if (myCountry && myProfile.get("country") !== myCountry) tx.update(myProfile.ref, { country: myCountry });

    const waiting = await tx.get(db.collection("queue").where("status", "==", "waiting").limit(100));
    const fresh = waiting.docs.filter((d) => d.id !== uid && now - ((d.get("heartbeat") as number) ?? 0) < QUEUE_STALE_MS);
    // 비슷한 점수끼리 (오래 기다릴수록 범위가 넓어지고 1분 뒤엔 아무나)
    const myScore = (myProfile.get("score") as number | undefined) ?? START_SCORE;
    const myCreatedAt = me.exists && me.get("status") === "waiting" ? (me.get("createdAt") as number) : now;
    const picked = pickOpponent(
      { id: uid, score: myScore, createdAt: myCreatedAt },
      fresh.map((d) => ({ id: d.id, score: (d.get("score") as number | undefined) ?? START_SCORE, createdAt: d.get("createdAt") as number })),
      now,
    );
    const opp = picked ? fresh.find((d) => d.id === picked.id) : undefined;
    const oppProfile = opp ? await tx.get(db.collection("users").doc(opp.id)) : null;

    const stillWaiting = me.exists && me.get("status") === "waiting";
    if (!opp || !oppProfile?.exists) {
      tx.set(meRef, {
        status: "waiting",
        heartbeat: now,
        createdAt: stillWaiting ? me.get("createdAt") : now,
        score: myScore,
        country: myCountry,
        gameId: null,
      });
      return { gameId: null };
    }

    const gameRef = db.collection("games").doc();
    const iAmCat = Math.random() < 0.5;
    const [cat, ham] = iAmCat ? [myProfile, oppProfile] : [oppProfile, myProfile];
    // 동물은 소모품: 장착한 동물이 남아 있으면 이번 판에 1개 사용
    const catAnimal = useAnimal(tx, cat);
    const hamAnimal = useAnimal(tx, ham);
    const catBoost = useBoost(tx, cat);
    const hamBoost = useBoost(tx, ham);
    tx.set(gameRef, {
      catUid: cat.id,
      hamUid: ham.id,
      catName: cat.get("name") ?? "고양이",
      hamName: ham.get("name") ?? "햄스터",
      catScore: cat.get("score") ?? START_SCORE,
      hamScore: ham.get("score") ?? START_SCORE,
      cat: [1, 1],
      ham: [1, 1],
      catSplits: MAX_SPLITS,
      hamSplits: MAX_SPLITS,
      // 랜덤 매칭은 부활 1번까지
      catRevives: revivesForGame(cat.get("potions"), RANKED_MAX_REVIVES),
      hamRevives: revivesForGame(ham.get("potions"), RANKED_MAX_REVIVES),
      catCountry: iAmCat ? myCountry : cleanCountry(opp.get("country")),
      hamCountry: iAmCat ? cleanCountry(opp.get("country")) : myCountry,
      catSkin: catAnimal,
      hamSkin: hamAnimal,
      catStreak: ribbons(cat),
      hamStreak: ribbons(ham),
      catBoost,
      hamBoost,
      catTurnMs: turnLimitAgainst(hamAnimal),
      hamTurnMs: turnLimitAgainst(catAnimal),
      turn: "CAT",
      status: "playing",
      winner: null,
      reason: "",
      lastDesc: "",
      explodedSide: null,
      explodedIndex: -1,
      moveNo: 0,
      turnStartedAt: now,
      createdAt: now,
    });
    const matched = { status: "matched", gameId: gameRef.id, heartbeat: now };
    tx.set(meRef, { ...matched, createdAt: now });
    tx.update(opp.ref, matched);
    return { gameId: gameRef.id };
  });
});

/** 매칭 취소. */
export const leaveQueue = onCall(async (req) => {
  const uid = requireUser(req);
  const ref = db.collection("queue").doc(uid);
  await db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (s.exists && s.get("status") === "waiting") tx.delete(ref);
  });
  return { ok: true };
});

/** 수 두기. 차례·규칙을 서버에서 검증하고, 끝나면 점수까지 반영합니다. */
export const submitMove = onCall(async (req) => {
  const uid = requireUser(req);
  const gameId = requireGameId(req.data);
  const move = decodeMove((req.data as { move?: unknown }).move);
  if (!move) throw err("invalid-argument", "잘못된 수예요", "Invalid move");

  await db.runTransaction(async (tx) => {
    const { ref, game, side } = await loadGame(tx, gameId, uid);
    if (game.status !== "playing") throw err("failed-precondition", "이미 끝난 게임이에요", "This game is already over");
    if (game.turn !== side) throw err("failed-precondition", "내 차례가 아니에요", "It's not your turn");
    // 시간을 넘겨 도착한 수는 받지 않고 시간 초과 패배
    if (isLate(game)) {
      finishGame(tx, ref, game, opponent(side), "timeout", await readPlayers(tx, game));
      return false;
    }
    const board: Board = {
      cat: game.cat,
      ham: game.ham,
      catSplits: game.catSplits ?? MAX_SPLITS,
      hamSplits: game.hamSplits ?? MAX_SPLITS,
      catRevives: game.catRevives ?? 0,
      hamRevives: game.hamRevives ?? 0,
    };
    if (!isLegal(board, side, move)) throw err("invalid-argument", "규칙에 맞지 않는 수예요", "That move breaks the rules");

    // 부활물약은 실제로 가진 물약을 하나 소모 (읽기는 쓰기보다 먼저)
    const myRef = db.collection("users").doc(uid);
    const mySkin = side === "CAT" ? game.catSkin : game.hamSkin;
    const luckyAttack = mySkin === LUCKY_SKIN && move.kind === "add" && move.toOpponent;
    // 수 결과는 계산만 먼저 (읽기는 모두 쓰기 전에)
    const r = applyMove(board, side, move);
    // 🐉 아기 용: 상대 방울을 터뜨리면 💰 +2 (하루 최대 10)
    const dragonPop = mySkin === DRAGON_SKIN && r.exploded !== null && r.exploded.side !== side;
    const mine = move.kind === "revive" || luckyAttack || dragonPop ? await tx.get(myRef) : null;
    if (move.kind === "revive" && (((mine?.get("potions") as number | undefined) ?? 0) < 1)) {
      throw err("failed-precondition", "부활물약이 없어요", "You have no revive potions");
    }
    // 💰 복냥이: 공격하면 50% 확률로 상점 포인트 +1 (하루 최대 30, 판정은 서버에서만)
    const today = dayKey(Date.now());
    const luckyToday = mine && mine.get("luckyDay") === today ? ((mine.get("luckyCoins") as number | undefined) ?? 0) : 0;
    const lucky = luckyAttack && mine !== null && luckyRoll(luckyToday, Math.random());
    const dragonToday = mine && mine.get("dragonDay") === today ? ((mine.get("dragonCoins") as number | undefined) ?? 0) : 0;
    const dragonGot = dragonPop && mine !== null ? dragonCoins(dragonToday) : 0;
    const update = {
      cat: r.board.cat,
      ham: r.board.ham,
      catSplits: r.board.catSplits,
      hamSplits: r.board.hamSplits,
      catRevives: r.board.catRevives ?? 0,
      hamRevives: r.board.hamRevives ?? 0,
      turn: opponent(side),
      lastDesc: r.description,
      lastDescEn: r.descriptionEn,
      lastBy: side,
      lastMove: encodeMove(move),
      lastPower: r.info.power,
      lastSum: r.info.sum,
      lastBoom: r.info.boom,
      lastLeft: r.info.splitsLeft,
      explodedSide: r.exploded?.side ?? null,
      explodedIndex: r.exploded?.index ?? -1,
      lastLucky: lucky,
      /** 이번 수로 받은 💰 (복냥이 1 · 아기 용 2) */
      lastCoins: (lucky ? 1 : 0) + dragonGot,
      moveNo: game.moveNo + 1,
      turnStartedAt: Date.now(),
      // 이번 판에 캐릭터로 받은 💰 합계 (결과 화면에 보여줌)
      ...((lucky ? 1 : 0) + dragonGot > 0 ? { [side === "CAT" ? "catCoins" : "hamCoins"]: FieldValue.increment((lucky ? 1 : 0) + dragonGot) } : {}),
    };
    const win = winner(r.board);
    const players = win ? await readPlayers(tx, game) : null;
    if (move.kind === "revive") tx.update(myRef, { potions: FieldValue.increment(-1) });
    if (lucky) tx.update(myRef, { coins: FieldValue.increment(1), luckyDay: today, luckyCoins: luckyToday + 1 });
    if (dragonGot > 0) tx.update(myRef, { coins: FieldValue.increment(dragonGot), dragonDay: today, dragonCoins: dragonToday + dragonGot });
    if (win && players) {
      finishGame(tx, ref, game, win, "normal", players, update);
    } else {
      tx.update(ref, update);
    }
    return game.vsAi && !win;
  }).then(async (aiNext) => {
    if (aiNext) {
      await sleep(AI_THINK_MS);
      await aiTurn(gameId);
    }
  });
  return { ok: true };
});

/** AI 차례면 AI가 한 수 둡니다. */
async function aiTurn(gameId: string) {
  const ref = db.collection("games").doc(gameId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const game = snap.data() as Game;
    if (!game.vsAi || game.status !== "playing") return;
    const aiSide: Side = game.catUid === AI_UID ? "CAT" : "HAMSTER";
    if (game.turn !== aiSide) return;
    const board: Board = {
      cat: game.cat, ham: game.ham,
      catSplits: game.catSplits ?? MAX_SPLITS, hamSplits: game.hamSplits ?? MAX_SPLITS,
      catRevives: game.catRevives ?? 0, hamRevives: game.hamRevives ?? 0,
    };
    const aiMove = chooseMove(board, aiSide, game.vsAi);
    const r = applyMove(board, aiSide, aiMove);
    const update = {
      cat: r.board.cat, ham: r.board.ham,
      catSplits: r.board.catSplits, hamSplits: r.board.hamSplits,
      catRevives: r.board.catRevives ?? 0, hamRevives: r.board.hamRevives ?? 0,
      turn: opponent(aiSide),
      lastDesc: r.description,
      lastDescEn: r.descriptionEn,
      lastBy: aiSide,
      lastMove: encodeMove(aiMove),
      lastPower: r.info.power,
      lastSum: r.info.sum,
      lastBoom: r.info.boom,
      lastLeft: r.info.splitsLeft,
      explodedSide: r.exploded?.side ?? null,
      explodedIndex: r.exploded?.index ?? -1,
      lastLucky: false,
      lastCoins: 0,
      moveNo: game.moveNo + 1,
      turnStartedAt: Date.now(),
    };
    const win = winner(r.board);
    if (win) {
      finishGame(tx, ref, game, win, "normal", await readPlayers(tx, game), update);
    } else {
      tx.update(ref, update);
    }
  });
}

/**
 * AI 대전 시작 (점수가 걸린 대전). 판과 AI의 수는 모두 서버가 처리합니다.
 * 점수: 쉬움 +1/0, 보통 +5/-3, 어려움 +10/-5.
 */
export const startAiGame = onCall(async (req) => {
  const uid = requireUser(req);
  const level = (req.data as { level?: unknown } | null)?.level;
  const country = cleanCountry((req.data as { country?: unknown } | null)?.country);
  if (!isAiLevel(level)) throw err("invalid-argument", "난이도를 골라 주세요", "Please choose a difficulty");
  // ×3 부스터는 랜덤 매칭 전용이라 AI 대전에서는 쓰지 않음
  const me = await db.collection("users").doc(uid).get();
  if (!me.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
  if (country && me.get("country") !== country) await me.ref.update({ country });
  const humanIsCat = Math.random() < 0.5;
  const now = Date.now();
  const gameRef = db.collection("games").doc();
  const aiName = `AI 친구 (${AI_LABEL[level]})`;
  const myName = me.get("name") ?? "나";
  const myScore = me.get("score") ?? START_SCORE;
  const myRevives = revivesForGame(me.get("potions"));
  const eq = me.get("equipped") as string | null | undefined;
  const aiSkin = lookSkin(eq, me.get("skins"), (me.get("score") as number | undefined) ?? START_SCORE) ?? (eq && ((me.get(`animals.${eq}`) as number | undefined) ?? 0) > 0 ? eq : null);
  await gameRef.set({
    vsAi: level,
    catCountry: humanIsCat ? country : null,
    hamCountry: humanIsCat ? null : country,
    catUid: humanIsCat ? uid : AI_UID,
    hamUid: humanIsCat ? AI_UID : uid,
    catName: humanIsCat ? myName : aiName,
    hamName: humanIsCat ? aiName : myName,
    catScore: humanIsCat ? myScore : 0,
    hamScore: humanIsCat ? 0 : myScore,
    // AI 대전은 시간 제한이 없어 동물을 쓰지 않음 (남아 있으면 모습만)
    catSkin: humanIsCat ? aiSkin : null,
    hamSkin: humanIsCat ? null : aiSkin,
    catStreak: humanIsCat ? ribbons(me) : 0,
    hamStreak: humanIsCat ? 0 : ribbons(me),
    cat: [1, 1],
    ham: [1, 1],
    catSplits: MAX_SPLITS,
    hamSplits: MAX_SPLITS,
    catRevives: humanIsCat ? myRevives : 0,
    hamRevives: humanIsCat ? 0 : myRevives,
    catTurnMs: AI_TURN_MS[level],
    hamTurnMs: AI_TURN_MS[level],
    turn: "CAT",
    status: "playing",
    winner: null,
    reason: "",
    lastDesc: "",
    explodedSide: null,
    explodedIndex: -1,
    moveNo: 0,
    turnStartedAt: now,
    createdAt: now,
  });
  if (!humanIsCat) {
    // AI가 고양이면 먼저 둠 (앱이 화면을 띄울 시간을 조금 줌)
    await sleep(AI_THINK_MS + 300);
    await aiTurn(gameRef.id);
  }
  return { gameId: gameRef.id };
});

/** 기권 (게임 중 나가기). */
export const forfeit = onCall(async (req) => {
  const uid = requireUser(req);
  const gameId = requireGameId(req.data);
  await db.runTransaction(async (tx) => {
    const { ref, game, side } = await loadGame(tx, gameId, uid);
    if (game.status !== "playing") return;
    const players = await readPlayers(tx, game);
    finishGame(tx, ref, game, opponent(side), "forfeit", players);
  });
  return { ok: true };
});

/** 상대가 제한 시간을 넘겼을 때 승리를 가져옵니다. 시간은 서버 시계로 판단합니다. */
export const claimTimeout = onCall(async (req) => {
  const uid = requireUser(req);
  const gameId = requireGameId(req.data);
  const won = await db.runTransaction(async (tx) => {
    const { ref, game, side } = await loadGame(tx, gameId, uid);
    if (game.status !== "playing") return false;
    // 온라인: 상대 차례가 시간 초과면 내가 승리 / AI 대전: 내 차례가 시간 초과면 AI 승리
    const lateSide = game.vsAi ? side : opponent(side);
    if (game.turn !== lateSide || !isLate(game)) return false;
    finishGame(tx, ref, game, opponent(lateSide), "timeout", await readPlayers(tx, game));
    return !game.vsAi;
  });
  return { won };
});

const lookupKey = googleKeyLookup();

/**
 * AdMob 서버 측 확인(SSV) 콜백. AdMob 콘솔의 보상형 광고 단위 설정에 이 함수의 URL을 넣습니다.
 * 서명이 맞고, 그 사용자가 해당 게임에서 졌고, 아직 보상받지 않았으면 점수를 돌려줍니다.
 */
export const admobSsv = onRequest(async (req, res) => {
  const raw = req.originalUrl.split("?")[1] ?? "";
  // AdMob 콘솔의 "확인" 버튼은 파라미터 없이 호출하므로 200으로 응답
  if (!raw) {
    res.status(200).send("ok");
    return;
  }
  let p;
  try {
    p = await verifySsv(raw, lookupKey);
  } catch (e) {
    logger.error("SSV verify error", e);
    res.status(500).send("verify error");
    return;
  }
  if (!p) {
    res.status(403).send("bad signature");
    return;
  }
  if (!REWARDED_AD_UNITS.includes(p.adUnit)) {
    logger.info("SSV for other ad unit ignored", { adUnit: p.adUnit });
    res.status(200).send("ignored");
    return;
  }
  const gameId = p.customData;
  if (!p.userId || !p.transactionId || !/^[A-Za-z0-9]{1,40}$/.test(gameId)) {
    res.status(200).send("ignored");
    return;
  }

  // 상점의 "광고 보고 💖 부활물약 받기" (하루 POTION_ADS_PER_DAY번)
  if (gameId === "potion") {
    const potionResult = await db.runTransaction(async (tx) => {
      const rewardRef = db.collection("adRewards").doc(p.transactionId.replace(/[^A-Za-z0-9_-]/g, "_"));
      const userRef = db.collection("users").doc(p.userId);
      const [done, u] = await Promise.all([tx.get(rewardRef), tx.get(userRef)]);
      if (done.exists) return "duplicate";
      if (!u.exists) return "no user";
      const today = dayKey(Date.now());
      const used = potionAdsToday(u.data() ?? {}, today);
      tx.create(rewardRef, { uid: p.userId, kind: "potion", at: Date.now(), granted: used < POTION_ADS_PER_DAY });
      if (used >= POTION_ADS_PER_DAY) return "daily limit";
      tx.update(userRef, { potions: FieldValue.increment(1), potionAdDay: today, potionAds: used + 1 });
      return "potion +1";
    });
    logger.info("SSV potion", { uid: p.userId, result: potionResult });
    res.status(200).send(potionResult);
    return;
  }

  const result = await db.runTransaction(async (tx) => {
    const rewardRef = db.collection("adRewards").doc(p.transactionId.replace(/[^A-Za-z0-9_-]/g, "_"));
    if ((await tx.get(rewardRef)).exists) return "duplicate";
    const gameRef = db.collection("games").doc(gameId);
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) return "no game";
    const game = gameSnap.data() as Game & { winner?: Side; catDelta?: number; hamDelta?: number; catRefund?: number; hamRefund?: number };
    const side = sideOf(game, p.userId);
    if (!side || game.status !== "finished" || game.winner === side) return "not a loss";
    if (game.vsAi) return "ai game";
    if (game.vsFriend) return "friend game";
    const refundField = side === "CAT" ? "catRefund" : "hamRefund";
    if ((game[refundField] ?? 0) > 0) return "already refunded";
    const lost = -((side === "CAT" ? game.catDelta : game.hamDelta) ?? 0);
    const refund = Math.min(AD_REFUND_POINTS, Math.max(0, lost));
    tx.create(rewardRef, { uid: p.userId, gameId, refund, at: Date.now() });
    if (refund > 0) {
      tx.update(gameRef, { [refundField]: refund });
      tx.update(db.collection("users").doc(p.userId), { score: FieldValue.increment(refund) });
    }
    return `refunded ${refund}`;
  });
  logger.info("SSV", { uid: p.userId, gameId, result });
  res.status(200).send(result);
});

/**
 * 상점 구매. 상점 포인트(coins, 결제로 충전)를 먼저 쓰고 모자라면 점수에서 씁니다.
 * - POTION: 부활물약 1개 (1000점)
 * - SHEEP / DOG / GIRAFFE: 동물 캐릭터 (사면 바로 장착)
 */
export const buyItem = onCall(async (req) => {
  const uid = requireUser(req);
  const item = (req.data as { item?: unknown } | null)?.item;
  const rawQty = (req.data as { qty?: unknown } | null)?.qty;
  const qty = rawQty === undefined || rawQty === null ? 1 : rawQty;
  if (item === "LAND") {
    // 🚩 땅: 점수로만 (💰 상점 포인트는 결제로 살 수 있어서 안 씀), 계정당 한 번
    if (qty !== 1) throw err("invalid-argument", "없는 묶음이에요", "No such bundle");
    const ref = db.collection("users").doc(uid);
    await db.runTransaction(async (tx) => {
      const u = await tx.get(ref);
      if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
      const score = (u.get("score") as number | undefined) ?? START_SCORE;
      const check = landCheck(u.get("land"), score);
      if (check === "owned") throw err("already-exists", "이미 우리 땅이 있어요", "You already own the land");
      if (check === "short") throw err("failed-precondition", `점수가 부족해요 (${LAND_PRICE}점 필요)`, `Not enough score (${LAND_PRICE} needed)`);
      tx.update(ref, { score: FieldValue.increment(-LAND_PRICE), land: true, landAt: Date.now() });
    });
    logger.info("land bought", { uid });
    return { ok: true };
  }
  if (typeof item !== "string" || (item !== "POTION" && item !== "BOOST" && item !== "RENAME" && !ANIMALS[item] && !isCosmetic(item))) {
    throw err("invalid-argument", "없는 상품이에요", "No such item");
  }
  // 물약·동물은 묶음 할인 (1·3·5·10개), 나머지는 1개씩
  const bundle = BUNDLES[item] ? bundlePrice(item, qty) : (qty === 1 ? 0 : null);
  if (bundle === null) throw err("invalid-argument", "없는 묶음이에요", "No such bundle");
  const count = BUNDLES[item] ? (qty as number) : 1;
  const price = BUNDLES[item] ? bundle : item === "POTION" ? POTION_PRICE : item === "BOOST" ? BOOST_PRICE : item === "RENAME" ? RENAME_PRICE
    : isCosmetic(item) ? COSMETIC_SKINS[item] : ANIMALS[item].price;
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const u = await tx.get(userRef);
    if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    if (isCosmetic(item) && ((u.get("skins") as unknown[] | undefined) ?? []).includes(item)) {
      throw err("already-exists", "이미 가진 스킨이에요", "You already own this skin");
    }
    const score = (u.get("score") as number | undefined) ?? START_SCORE;
    const coins = (u.get("coins") as number | undefined) ?? 0;
    // 상점 포인트(결제)를 먼저 쓰고, 모자라는 만큼 점수에서
    const pay = splitPayment(price, coins, score);
    if (!pay) throw err("failed-precondition", `포인트가 부족해요 (${price} 필요)`, `Not enough points (${price} needed)`);
    const { fromCoins, fromScore } = pay;
    tx.update(
      userRef,
      item === "POTION"
        ? { score: FieldValue.increment(-fromScore), coins: FieldValue.increment(-fromCoins), potions: FieldValue.increment(count) }
        : isCosmetic(item)
        // 유료 스킨: 한 번 사면 계속 (바로 장착)
        ? { score: FieldValue.increment(-fromScore), coins: FieldValue.increment(-fromCoins), skins: FieldValue.arrayUnion(item), equipped: item }
        : item === "RENAME"
        // ✏️ 닉네임 변경권 1장
        ? { score: FieldValue.increment(-fromScore), coins: FieldValue.increment(-fromCoins), renameTickets: FieldValue.increment(1) }
        : item === "BOOST"
        // 부스터: 1개 추가하고 바로 켬
        ? { score: FieldValue.increment(-fromScore), coins: FieldValue.increment(-fromCoins), boosts: FieldValue.increment(1), boostOn: true }
        : {
          // 동물은 소모품: 1개 추가하고 바로 장착
          score: FieldValue.increment(-fromScore), coins: FieldValue.increment(-fromCoins),
          [`animals.${item}`]: FieldValue.increment(count), equipped: item,
        },
    );
  });
  return { ok: true };
});

/** 가진 동물 캐릭터 장착 (null이면 기본 고양이/햄스터). 다음 판부터 적용됩니다. */
export const equipAnimal = onCall(async (req) => {
  const uid = requireUser(req);
  const animal = (req.data as { animal?: unknown } | null)?.animal ?? null;
  if (animal !== null && (typeof animal !== "string" || (!ANIMALS[animal] && !isFreeSkin(animal) && !isCosmetic(animal) && !isRankSkin(animal)))) {
    throw err("invalid-argument", "없는 캐릭터예요", "No such character");
  }
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const u = await tx.get(userRef);
    const left = animal === null ? 0 : ((u.get(`animals.${animal}`) as number | undefined) ?? 0);
    // 무료 캐릭터는 가진 개수와 상관없이 장착
    if (isRankSkin(animal)) {
      // 🦄 계급 캐릭터: 팔지 않고, 계급이 되면 장착
      if (((u.get("score") as number | undefined) ?? START_SCORE) < RANK_SKINS[animal]) {
        throw err("failed-precondition", "다이아 계급부터 장착할 수 있어요", "Unlocks at Diamond rank");
      }
    } else if (isCosmetic(animal)) {
      const skins = u.get("skins") as unknown;
      if (!Array.isArray(skins) || !skins.includes(animal)) {
        throw err("failed-precondition", "아직 없는 스킨이에요", "You don't own this skin yet");
      }
    } else if (animal !== null && !isFreeSkin(animal) && left <= 0) {
      throw err("failed-precondition", "아직 없는 캐릭터예요", "You don't own this character yet");
    }
    tx.update(userRef, { equipped: animal });
  });
  return { ok: true };
});

/**
 * 계정 삭제 (플레이 스토어 정책: 앱 안에서 계정과 데이터를 지울 수 있어야 함).
 * 1) 진행 중인 대전은 기권 처리 (상대 승리, 점수 반영)
 * 2) 매칭 대기열과 프로필(점수·아이템) 삭제
 * 3) 구글 로그인 계정(Firebase Auth) 삭제
 * 끝난 대전 기록은 부정행위 확인을 위해 남겨 두며, 개인정보처리방침에 적힌 대로 최대 1년 보관합니다.
 */
export const deleteAccount = onCall(async (req) => {
  const uid = requireUser(req);
  const games = db.collection("games");
  const [asCat, asHam] = await Promise.all([
    games.where("catUid", "==", uid).where("status", "==", "playing").get(),
    games.where("hamUid", "==", uid).where("status", "==", "playing").get(),
  ]);
  for (const g of [...asCat.docs, ...asHam.docs]) {
    await db.runTransaction(async (tx) => {
      const { ref, game, side } = await loadGame(tx, g.id, uid);
      if (game.status !== "playing") return;
      finishGame(tx, ref, game, opponent(side), "forfeit", await readPlayers(tx, game));
    });
  }
  // 같은 구글 계정으로 다시 가입하면 0점에서 시작하도록, 이메일 대신 해시만 남김
  const email = req.auth?.token.email;
  if (typeof email === "string") {
    await db.collection("deletedAccounts").doc(emailHash(email)).set({ at: Date.now() });
  }
  await db.collection("queue").doc(uid).delete();
  await db.collection("presence").doc(uid).delete();
  await db.collection("users").doc(uid).delete();
  try {
    await getAuth().deleteUser(uid);
  } catch (e) {
    logger.error("auth delete failed", { uid, e });
    throw err("internal", "계정 삭제 중 문제가 생겼어요. 다시 시도해 주세요", "Something went wrong deleting your account. Please try again");
  }
  logger.info("account deleted", { uid, forfeited: asCat.size + asHam.size });
  return { ok: true };
});

// ======================= 친구 추천 =======================

/** 친구를 초대한 사람이 받는 점수 */
const REFERRAL_POINTS = 30;
/** 한 사람이 초대 보상을 받을 수 있는 최대 인원 (부계정 악용 방지) */
const MAX_REFERRALS = 100;

/** 내 추천 코드 (없으면 새로 만듦). refCodes/{code} → uid 로 중복을 막습니다. */
export const getReferralCode = onCall(async (req) => {
  const uid = requireUser(req);
  const userRef = db.collection("users").doc(uid);
  const existing = (await userRef.get()).get("refCode") as string | undefined;
  if (existing) return { code: existing };
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomCode();
    const codeRef = db.collection("refCodes").doc(code);
    const made = await db.runTransaction(async (tx) => {
      const [u, c] = await Promise.all([tx.get(userRef), tx.get(codeRef)]);
      if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
      const already = u.get("refCode") as string | undefined;
      if (already) return already;
      if (c.exists) return null;
      tx.set(codeRef, { uid });
      tx.update(userRef, { refCode: code });
      return code;
    });
    if (made) return { code: made };
  }
  throw err("internal", "코드를 만들지 못했어요. 다시 시도해 주세요", "Could not create a code. Please try again");
});

/** 친구의 추천 코드 입력. 계정당 한 번, 초대한 사람과 초대받은 사람 둘 다 +30점. */
export const redeemReferral = onCall(async (req) => {
  const uid = requireUser(req);
  const code = normalizeCode((req.data as { code?: unknown } | null)?.code);
  if (!code) throw err("invalid-argument", "코드 형식이 맞지 않아요 (6자리)", "That code doesn't look right (6 characters)");
  const meRef = db.collection("users").doc(uid);
  const codeRef = db.collection("refCodes").doc(code);
  await db.runTransaction(async (tx) => {
    const [me, c] = await Promise.all([tx.get(meRef), tx.get(codeRef)]);
    if (!me.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    if (me.get("referredBy")) throw err("already-exists", "이미 추천 코드를 입력했어요", "You've already entered a referral code");
    if (!c.exists) throw err("not-found", "없는 추천 코드예요", "No such referral code");
    const inviter = c.get("uid") as string;
    if (inviter === uid) throw err("invalid-argument", "내 코드는 입력할 수 없어요", "You can't use your own code");
    const inviterRef = db.collection("users").doc(inviter);
    const inv = await tx.get(inviterRef);
    if (!inv.exists) throw err("not-found", "없는 추천 코드예요", "No such referral code");
    const count = (inv.get("referrals") as number | undefined) ?? 0;
    // 초대받은 사람: 항상 +30 (계정당 한 번) / 초대한 사람: 최대 MAX_REFERRALS명까지 +30
    tx.update(meRef, { referredBy: inviter, score: FieldValue.increment(REFERRAL_POINTS) });
    if (count < MAX_REFERRALS) {
      tx.update(inviterRef, { score: FieldValue.increment(REFERRAL_POINTS), referrals: FieldValue.increment(1) });
    }
  });
  return { ok: true };
});

// ======================= 인앱 결제 =======================

const PACKAGE_NAME = "com.softrime.bubble";
/** 아이폰 앱 번들 ID (App Store). 안드로이드 패키지 이름과 같음 */
const APPLE_BUNDLE_ID = "com.softrime.bubble";
/** Play Console에 만든 소모성 상품 ID → 지급 점수 */
/** 결제로 받는 것은 "상점 포인트(coins)" — 상점 전용이라 점수·계급에는 들어가지 않음 */
const PRODUCTS: Record<string, number> = { points_500: 500, points_1000: 1000, points_2000: 2000, points_5000: 5000 };
/** 광고 제거 (한 번 사면 계속, 비소모성). 전면 광고만 안 나오고, 보고 싶을 때 보는 보상형 광고는 그대로 */
const REMOVE_ADS = "remove_ads";

/**
 * 영수증 한 건을 계정에 반영 (트랜잭션 안).
 * - 포인트 상품: 상점 포인트 지급. 가입 후 7일 안의 첫 포인트 구매면 2배 (스타터 혜택)
 * - 광고 제거: noAds = true
 * 돌려주는 값: 지급한 포인트 (광고 제거는 0)
 */
function grantPurchase(tx: Transaction, user: DocumentSnapshot, productId: string): number {
  if (productId === REMOVE_ADS) {
    tx.update(user.ref, { noAds: true });
    return 0;
  }
  const base = PRODUCTS[productId] ?? 0;
  // 첫 구매 2배: 가입 후 7일 안에 처음 사는 포인트만
  const first = firstBuyBonus(user.get("firstBuyDone"), user.get("createdAt"), Date.now());
  const points = first ? base * 2 : base;
  tx.update(user.ref, { coins: FieldValue.increment(points), firstBuyDone: true });
  return points;
}

const playAuth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });

/**
 * 결제 영수증 확인 후 점수 지급.
 * Google Play Developer API로 영수증이 진짜이고 결제 완료 상태인지 확인하고,
 * purchases/{token} 기록으로 같은 영수증을 두 번 쓰지 못하게 합니다.
 * (Cloud Functions 서비스 계정을 Play Console 사용자로 초대해 두어야 합니다 — README 참고)
 */
export const verifyPurchase = onCall(async (req) => {
  const uid = requireUser(req);
  const data = (req.data ?? {}) as { productId?: unknown; token?: unknown };
  const productId = typeof data.productId === "string" ? data.productId : "";
  const token = typeof data.token === "string" ? data.token : "";
  const points = PRODUCTS[productId];
  if ((!points && productId !== REMOVE_ADS) || !token || token.length > 4096) throw err("invalid-argument", "잘못된 결제 정보예요", "Invalid purchase");

  const purchaseRef = db.collection("purchases").doc(token.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 1400));
  const done = await purchaseRef.get();
  if (done.exists) {
    if (done.get("uid") !== uid) throw err("permission-denied", "다른 계정의 결제예요", "This purchase belongs to another account");
    // 광고 제거는 앱을 다시 깔아도 다시 확인되므로, 표시가 빠져 있으면 다시 켬
    if (productId === REMOVE_ADS) await db.collection("users").doc(uid).update({ noAds: true });
    return { granted: 0, already: true };
  }

  const client = await playAuth.getClient();
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}` +
    `/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`;
  let info: { purchaseState?: number; consumptionState?: number; orderId?: string };
  try {
    info = (await client.request({ url })).data as typeof info;
  } catch (e) {
    logger.error("play verify failed", { uid, productId, e });
    throw err("failed-precondition", "결제를 확인하지 못했어요. 잠시 후 다시 시도해 주세요", "Couldn't verify the purchase. Please try again shortly");
  }
  // purchaseState 0 = 결제 완료 (1 취소, 2 대기)
  if (info.purchaseState !== 0) throw err("failed-precondition", "결제가 아직 완료되지 않았어요", "The payment isn't complete yet");

  const granted = await db.runTransaction(async (tx) => {
    const [again, user] = await Promise.all([tx.get(purchaseRef), tx.get(db.collection("users").doc(uid))]);
    if (again.exists) return 0;
    if (!user.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    const g = grantPurchase(tx, user, productId);
    tx.create(purchaseRef, { uid, productId, points: g, orderId: info.orderId ?? null, at: Date.now() });
    return g;
  });
  logger.info("purchase granted", { uid, productId, orderId: info.orderId, granted });
  return { granted };
});

/**
 * App Store 결제 확인 후 상점 포인트 지급 (아이폰).
 * 앱이 보낸 StoreKit 2 거래 JWS의 Apple 서명을 확인하고(apple.ts), 상품·앱·계정이 맞는지 본 뒤
 * purchases/apple_{transactionId} 기록으로 같은 거래를 두 번 쓰지 못하게 합니다.
 */
export const verifyApplePurchase = onCall(async (req) => {
  const uid = requireUser(req);
  const jws = (req.data as { jws?: unknown } | null)?.jws;
  if (typeof jws !== "string") throw err("invalid-argument", "잘못된 결제 정보예요", "Invalid purchase");
  const t = verifyAppleJws(jws);
  if (!t) throw err("permission-denied", "결제를 확인하지 못했어요", "Couldn't verify the purchase");
  const points = PRODUCTS[t.productId];
  if (t.bundleId !== APPLE_BUNDLE_ID || (!points && t.productId !== REMOVE_ADS)) throw err("invalid-argument", "잘못된 결제 정보예요", "Invalid purchase");
  if (t.revocationDate) throw err("failed-precondition", "환불된 결제예요", "This purchase was refunded");
  // 앱이 결제할 때 넣은 계정 표시와 같아야 함 (다른 계정의 영수증 재사용 방지)
  if (!t.appAccountToken || t.appAccountToken.toLowerCase() !== appAccountToken(uid)) {
    throw err("permission-denied", "다른 계정의 결제예요", "This purchase belongs to another account");
  }
  const purchaseRef = db.collection("purchases").doc(`apple_${t.transactionId.replace(/[^0-9A-Za-z]/g, "")}`);
  const granted = await db.runTransaction(async (tx) => {
    const userRef = db.collection("users").doc(uid);
    const [done, user] = await Promise.all([tx.get(purchaseRef), tx.get(userRef)]);
    if (!user.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    if (done.exists) {
      if (done.get("uid") !== uid) throw err("permission-denied", "다른 계정의 결제예요", "This purchase belongs to another account");
      // 구매 복원: 광고 제거 표시만 다시 켬
      if (t.productId === REMOVE_ADS) tx.update(userRef, { noAds: true });
      return 0;
    }
    const g = grantPurchase(tx, user, t.productId);
    tx.create(purchaseRef, {
      uid, productId: t.productId, points: g, store: "apple", environment: t.environment ?? null,
      orderId: t.transactionId, at: Date.now(),
    });
    return g;
  });
  logger.info("apple purchase granted", { uid, productId: t.productId, transactionId: t.transactionId, env: t.environment, granted });
  return { granted };
});

// ======================= 가입 / 관리자 / 동시 접속 =======================

/** 관리자 계정 (가입 시 5000점, 동시 접속자 수 보기) */
const ADMIN_EMAILS = ["softrime0@gmail.com"];
const ADMIN_START_SCORE = 5000;
/** 탈퇴했다가 같은 구글 계정으로 다시 가입하면 시작 점수 */
const RETURNING_START_SCORE = 0;
/** 이 시간 안에 신호를 보낸 사용자를 "접속 중"으로 셈 */
const ONLINE_WINDOW_MS = 2 * 60_000;

function emailHash(email: string): string {
  return createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}

function isAdmin(req: CallableRequest): boolean {
  const email = req.auth?.token.email;
  return typeof email === "string" && req.auth?.token.email_verified === true &&
    ADMIN_EMAILS.includes(email.toLowerCase());
}

/**
 * 🛠 관리자: 닉네임(정확히 같은 것)이나 uid로 사용자 찾기. 빈 칸이면 나 자신.
 * 관리자 이메일(구글 로그인, 인증됨)만 쓸 수 있음.
 */
export const adminFindUser = onCall(async (req) => {
  const me = requireUser(req);
  if (!isAdmin(req)) throw err("permission-denied", "관리자만 쓸 수 있어요", "Admins only");
  const q = typeof (req.data as { query?: unknown } | null)?.query === "string" ? ((req.data as { query: string }).query).trim() : "";
  const users = db.collection("users");
  const docs: DocumentSnapshot[] = [];
  if (!q) {
    docs.push(await users.doc(me).get());
  } else {
    const byId = await users.doc(q).get().catch(() => null);
    if (byId?.exists) docs.push(byId);
    const byName = await users.where("name", "==", q).limit(10).get();
    for (const d of byName.docs) if (!docs.some((x) => x.id === d.id)) docs.push(d);
  }
  return {
    users: docs.filter((d) => d.exists).map((d) => ({
      uid: d.id,
      name: (d.get("name") as string | undefined) ?? "",
      score: (d.get("score") as number | undefined) ?? START_SCORE,
      coins: (d.get("coins") as number | undefined) ?? 0,
      potions: (d.get("potions") as number | undefined) ?? 0,
    })),
  };
});

/**
 * 🛠 관리자: 점수 · 💰 상점 포인트 · 💖 물약 수정 (넣은 값으로 바꿈, 0 ~ 1000만).
 * 바꾼 기록은 adminLogs에 남김.
 */
export const adminSetPoints = onCall(async (req) => {
  const me = requireUser(req);
  if (!isAdmin(req)) throw err("permission-denied", "관리자만 쓸 수 있어요", "Admins only");
  const data = (req.data ?? {}) as { uid?: unknown; score?: unknown; coins?: unknown; potions?: unknown };
  const target = typeof data.uid === "string" && data.uid ? data.uid : me;
  const score = adminValue(data.score);
  const coins = adminValue(data.coins);
  const potions = adminValue(data.potions);
  if (score === null || coins === null || potions === null) {
    throw err("invalid-argument", "0 이상 1000만 이하 숫자만 넣을 수 있어요", "Only whole numbers from 0 to 10,000,000");
  }
  const change: Record<string, number> = {};
  if (score !== undefined) change.score = score;
  if (coins !== undefined) change.coins = coins;
  if (potions !== undefined) change.potions = potions;
  if (Object.keys(change).length === 0) throw err("invalid-argument", "바꿀 값이 없어요", "Nothing to change");
  const ref = db.collection("users").doc(target);
  return db.runTransaction(async (tx) => {
    const u = await tx.get(ref);
    if (!u.exists) throw err("not-found", "없는 사용자예요", "No such user");
    const before = { score: u.get("score") ?? null, coins: u.get("coins") ?? null, potions: u.get("potions") ?? null };
    tx.update(ref, change);
    tx.set(db.collection("adminLogs").doc(), { by: me, uid: target, before, after: change, at: Date.now() });
    return { ok: true, uid: target, ...change };
  });
});

/**
 * 첫 로그인 때 프로필 만들기 (서버만 만들 수 있음).
 * 시작 점수: 보통 1000점 · 탈퇴 후 재가입 0점 · 관리자 5000점.
 * 돌려주는 kind로 앱이 가입 환영 팝업을 띄웁니다 (이미 있으면 null).
 */
export const ensureProfile = onCall(async (req) => {
  const uid = requireUser(req);
  const admin = isAdmin(req);
  const rawName = (req.data as { name?: unknown } | null)?.name;
  const name = typeof rawName === "string" && rawName.trim() ? rawName.trim().slice(0, 12) : "Player";
  const country = cleanCountry((req.data as { country?: unknown } | null)?.country);
  const email = req.auth?.token.email;
  const deletedBefore = typeof email === "string"
    ? (await db.collection("deletedAccounts").doc(emailHash(email)).get()).exists
    : false;
  const ref = db.collection("users").doc(uid);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (snap.exists) {
      if (country && snap.get("country") !== country) tx.update(ref, { country });
      // 관리자는 처음 한 번 5000점까지 채워줌
      if (admin && !snap.get("adminSeeded")) {
        const score = (snap.get("score") as number | undefined) ?? START_SCORE;
        tx.update(ref, { admin: true, adminSeeded: true, score: Math.max(score, ADMIN_START_SCORE) });
      }
      return { kind: null };
    }
    const kind = admin ? "admin" : deletedBefore ? "returning" : "new";
    const score = admin ? ADMIN_START_SCORE : deletedBefore ? RETURNING_START_SCORE : START_SCORE;
    tx.set(ref, {
      name, score, wins: 0, losses: 0, createdAt: Date.now(),
      ...(country ? { country } : {}),
      ...(admin ? { admin: true, adminSeeded: true } : {}),
    });
    return { kind, score };
  });
});

/** 동시 접속자 수 (관리자 전용). 앱은 presence/{uid}에 1분마다 신호를 남깁니다. */
export const onlineCount = onCall(async (req) => {
  requireUser(req);
  if (!isAdmin(req)) throw err("permission-denied", "관리자만 볼 수 있어요", "Admins only");
  const since = Date.now() - ONLINE_WINDOW_MS;
  const agg = await db.collection("presence").where("at", ">", since).count().get();
  return { online: agg.data().count };
});

/** ×3 부스터 켜기/끄기 (켜두면 다음 랜덤 매칭 시작 때 1개 사용, AI 대전에는 안 씀) */
export const setBoost = onCall(async (req) => {
  const uid = requireUser(req);
  const on = (req.data as { on?: unknown } | null)?.on === true;
  const ref = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const u = await tx.get(ref);
    if (on && ((u.get("boosts") as number | undefined) ?? 0) <= 0) {
      throw err("failed-precondition", "부스터가 없어요", "You have no boosters");
    }
    tx.update(ref, { boostOn: on });
  });
  return { ok: true };
});

// ======================= 랭킹 (내 주변 순위) =======================

/** 랭킹 TOP 100 밖일 때 보여줄 내 위아래 인원 */
const AROUND_ME = 10;

/**
 * 내 순위와 내 위아래 10명씩. 순서는 점수 높은 순, 같은 점수면 문서 ID 역순
 * (앱의 TOP 100 목록 orderBy("score", desc)와 같은 순서).
 */
/** 다른 사람에게 보이는 캐릭터: 스킨(lookSkin) 또는 남아 있는 소모품 동물. 없으면 null(기본 고양이) */
function shownAnimal(d: DocumentSnapshot): string | null {
  const eq = d.get("equipped");
  const skin = lookSkin(eq, d.get("skins"), (d.get("score") as number | undefined) ?? START_SCORE);
  if (skin) return skin;
  return typeof eq === "string" && ANIMALS[eq] && ((d.get(`animals.${eq}`) as number | undefined) ?? 0) > 0 ? eq : null;
}

export const myRanking = onCall(async (req) => {
  const uid = requireUser(req);
  const users = db.collection("users");
  const me = await users.doc(uid).get();
  if (!me.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
  const myScore = (me.get("score") as number | undefined) ?? START_SCORE;
  const [higher, tiedAhead, aboveSnap, belowSnap] = await Promise.all([
    users.where("score", ">", myScore).count().get(),
    users.where("score", "==", myScore).where(FieldPath.documentId(), ">", uid).count().get(),
    users.orderBy("score", "asc").orderBy(FieldPath.documentId(), "asc").startAfter(myScore, uid).limit(AROUND_ME).get(),
    users.orderBy("score", "desc").orderBy(FieldPath.documentId(), "desc").startAfter(myScore, uid).limit(AROUND_ME).get(),
  ]);
  const rank = higher.data().count + tiedAhead.data().count + 1;
  const row = (d: DocumentSnapshot, r: number) => ({
    uid: d.id, rank: r, name: (d.get("name") as string | undefined) ?? "Player",
    score: (d.get("score") as number | undefined) ?? START_SCORE, country: (d.get("country") as string | undefined) ?? null,
    wins: (d.get("wins") as number | undefined) ?? 0, losses: (d.get("losses") as number | undefined) ?? 0,
    look: shownAnimal(d),
    land: d.get("land") === true,
  });
  const above = aboveSnap.docs.reverse().map((d, i, arr) => row(d, rank - (arr.length - i)));
  const below = belowSnap.docs.map((d, i) => row(d, rank + i + 1));
  return { rank, rows: [...above, row(me, rank), ...below] };
});

// ======================= 친구 대전 (초대 링크) =======================

function requireRoomCode(data: unknown): string {
  const code = normalizeCode((data as { code?: unknown } | null)?.code);
  if (!code) throw err("invalid-argument", "대전 코드가 맞지 않아요", "Invalid battle code");
  return code;
}

/** 친구 대전방 만들기. 돌려준 코드를 링크로 친구에게 보냄 (10분 동안 유효). */
export const createFriendRoom = onCall(async (req) => {
  const uid = requireUser(req);
  const country = cleanCountry((req.data as { country?: unknown } | null)?.country);
  const me = await db.collection("users").doc(uid).get();
  if (!me.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomCode();
    const ref = db.collection("friendRooms").doc(code);
    const made = await db.runTransaction(async (tx) => {
      const r = await tx.get(ref);
      if (r.exists && Date.now() - ((r.get("createdAt") as number | undefined) ?? 0) < FRIEND_ROOM_TTL_MS) return false;
      const now = Date.now();
      tx.set(ref, { host: uid, hostCountry: country, status: "waiting", gameId: null, createdAt: now, heartbeat: now });
      return true;
    });
    if (made) return { code };
  }
  throw err("internal", "대전방을 만들지 못했어요. 다시 시도해 주세요", "Could not create a room. Please try again");
});

/** 방장이 몇 초마다 호출: 친구가 들어왔으면 gameId, 아니면 null. 만료되면 expired. */
export const pollFriendRoom = onCall(async (req) => {
  const uid = requireUser(req);
  const code = requireRoomCode(req.data);
  const ref = db.collection("friendRooms").doc(code);
  return db.runTransaction(async (tx) => {
    const r = await tx.get(ref);
    if (!r.exists || r.get("host") !== uid) return { gameId: null, expired: true };
    if (r.get("status") === "matched") return { gameId: r.get("gameId") as string, expired: false };
    const now = Date.now();
    if (now - ((r.get("createdAt") as number | undefined) ?? 0) >= FRIEND_ROOM_TTL_MS) {
      tx.delete(ref);
      return { gameId: null, expired: true };
    }
    tx.update(ref, { heartbeat: now });
    return { gameId: null, expired: false };
  });
});

/** 방장이 기다리기를 취소. */
export const cancelFriendRoom = onCall(async (req) => {
  const uid = requireUser(req);
  const code = requireRoomCode(req.data);
  const ref = db.collection("friendRooms").doc(code);
  await db.runTransaction(async (tx) => {
    const r = await tx.get(ref);
    if (r.exists && r.get("host") === uid && r.get("status") === "waiting") tx.delete(ref);
  });
  return { ok: true };
});

/**
 * 친구가 링크로 들어옴 → 바로 대전 시작.
 * 점수: 이기면 +30, 지면 -10. 같은 두 사람은 하루(한국 시간) 5판까지만 점수가 걸리고, 그 뒤는 친선전.
 * 동물·×3 부스터는 쓰지 않음 (턴 시간 20초). 부활물약은 랜덤 매칭처럼 가진 만큼 쓸 수 있음.
 */
export const joinFriendRoom = onCall(async (req) => {
  const uid = requireUser(req);
  const code = requireRoomCode(req.data);
  const myCountry = cleanCountry((req.data as { country?: unknown } | null)?.country);
  const roomRef = db.collection("friendRooms").doc(code);
  return db.runTransaction(async (tx) => {
    const now = Date.now();
    const room = await tx.get(roomRef);
    if (!room.exists) throw err("not-found", "대전방이 없거나 끝났어요", "This battle room is gone");
    const host = room.get("host") as string;
    if (host === uid) throw err("invalid-argument", "내가 만든 방이에요. 친구에게 링크를 보내 주세요", "That's your own room. Send the link to a friend");
    if (room.get("status") === "matched") throw err("failed-precondition", "이미 다른 사람이 들어간 방이에요", "Someone already joined this room");
    if (!roomJoinable(room.data() ?? {}, now)) throw err("failed-precondition", "친구가 기다리기를 끝냈어요. 다시 초대해 달라고 해 주세요", "Your friend stopped waiting. Ask them to invite you again");
    const pairRef = db.collection("friendPairs").doc(`${pairKey(uid, host)}_${dayKey(now)}`);
    const [hostProfile, myProfile, pair] = await Promise.all([
      tx.get(db.collection("users").doc(host)),
      tx.get(db.collection("users").doc(uid)),
      tx.get(pairRef),
    ]);
    if (!myProfile.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    if (!hostProfile.exists) throw err("not-found", "대전방이 없거나 끝났어요", "This battle room is gone");
    const rated = isRated((pair.get("rated") as number | undefined) ?? 0);
    const hostCountry = cleanCountry(room.get("hostCountry"));
    const gameRef = db.collection("games").doc();
    const iAmCat = Math.random() < 0.5;
    const [cat, ham] = iAmCat ? [myProfile, hostProfile] : [hostProfile, myProfile];
    tx.set(gameRef, {
      vsFriend: true,
      rated,
      catUid: cat.id,
      hamUid: ham.id,
      catName: cat.get("name") ?? "고양이",
      hamName: ham.get("name") ?? "햄스터",
      catScore: cat.get("score") ?? START_SCORE,
      hamScore: ham.get("score") ?? START_SCORE,
      cat: [1, 1],
      ham: [1, 1],
      catSplits: MAX_SPLITS,
      hamSplits: MAX_SPLITS,
      catRevives: revivesForGame(cat.get("potions")),
      hamRevives: revivesForGame(ham.get("potions")),
      catCountry: iAmCat ? myCountry : hostCountry,
      hamCountry: iAmCat ? hostCountry : myCountry,
      // 친구 대전: 동물(효과)은 안 쓰고, 무료 캐릭터(햄스터 옷)는 모습만 보여줌
      catSkin: lookSkin(cat.get("equipped"), cat.get("skins"), (cat.get("score") as number | undefined) ?? START_SCORE),
      hamSkin: lookSkin(ham.get("equipped"), ham.get("skins"), (ham.get("score") as number | undefined) ?? START_SCORE),
      catStreak: ribbons(cat),
      hamStreak: ribbons(ham),
      catBoost: false,
      hamBoost: false,
      catTurnMs: TURN_LIMIT_MS,
      hamTurnMs: TURN_LIMIT_MS,
      turn: "CAT",
      status: "playing",
      winner: null,
      reason: "",
      lastDesc: "",
      explodedSide: null,
      explodedIndex: -1,
      moveNo: 0,
      turnStartedAt: now,
      createdAt: now,
    });
    if (rated) tx.set(pairRef, { rated: FieldValue.increment(1), at: now }, { merge: true });
    if (myCountry && myProfile.get("country") !== myCountry) tx.update(myProfile.ref, { country: myCountry });
    tx.update(roomRef, { status: "matched", gameId: gameRef.id, guest: uid });
    return { gameId: gameRef.id, rated };
  });
});

/**
 * ✏️ 닉네임 바꾸기: 처음 한 번은 무료, 그다음부터는 닉네임 변경권 1장 사용.
 * (예전에는 앱이 직접 바꿨지만 이제 서버만 바꿈 — firestore.rules에서 막음)
 */
export const rename = onCall(async (req) => {
  const uid = requireUser(req);
  const name = cleanName((req.data as { name?: unknown } | null)?.name);
  if (!name) throw err("invalid-argument", "닉네임을 입력해 주세요", "Please enter a nickname");
  const ref = db.collection("users").doc(uid);
  return db.runTransaction(async (tx) => {
    const u = await tx.get(ref);
    if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    if (u.get("name") === name) return { used: "same" };
    const cost = renameCost(u.get("freeRenameUsed"), u.get("renameTickets"));
    if (cost === "none") throw err("failed-precondition", "닉네임 변경권이 필요해요 (상점)", "You need a rename ticket (shop)");
    tx.update(ref, {
      name,
      ...(cost === "free" ? { freeRenameUsed: true } : { renameTickets: FieldValue.increment(-1) }),
    });
    return { used: cost };
  });
});

// ======================= 출석 · 일일 미션 · 주간 랭킹 보상 =======================

/** 오늘 출석 보상 받기 (한국 시간 하루 한 번). 7일 주기, 7일째는 💖 부활물약도 */
// 첫 배포 때 공개 호출 권한 설정이 실패했던 함수: 배포할 때마다 권한을 확실히 다시 설정하도록 명시
export const claimDaily = onCall({ invoker: "public" }, async (req) => {
  const uid = requireUser(req);
  const ref = db.collection("users").doc(uid);
  return db.runTransaction(async (tx) => {
    const u = await tx.get(ref);
    if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    const today = dayKey(Date.now());
    const day = nextStreak(u.get("checkinDay"), u.get("checkinStreak"), today);
    if (day === null) throw err("already-exists", "오늘은 이미 출석했어요", "You've already checked in today");
    const coins = CHECKIN_COINS[day - 1];
    const potion = day === CHECKIN_POTION_DAY;
    // 🐼 출석 판다를 장착하고 있으면 💰 +5
    const panda = pandaEquipped(u.get("equipped"), u.get("skins")) ? PANDA_CHECKIN_BONUS : 0;
    tx.update(ref, {
      checkinDay: today, checkinStreak: day, coins: FieldValue.increment(coins + panda),
      ...(potion ? { potions: FieldValue.increment(1) } : {}),
    });
    return { day, coins, potion, panda };
  });
});

/** 일일 미션 보상 받기 */
// 첫 배포 때 공개 호출 권한 설정이 실패했던 함수: 배포할 때마다 권한을 확실히 다시 설정하도록 명시
export const claimMission = onCall({ invoker: "public" }, async (req) => {
  const uid = requireUser(req);
  const id = (req.data as { id?: unknown } | null)?.id;
  const m = typeof id === "string" ? MISSIONS[id] : undefined;
  if (!m) throw err("invalid-argument", "없는 미션이에요", "No such mission");
  const ref = db.collection("users").doc(uid);
  return db.runTransaction(async (tx) => {
    const u = await tx.get(ref);
    if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    const today = dayKey(Date.now());
    const sameDay = u.get("missionDay") === today;
    const progress = sameDay ? ((u.get(m.field) as number | undefined) ?? 0) : 0;
    const claimed = sameDay ? ((u.get("mClaimed") as string[] | undefined) ?? []) : [];
    if (claimed.includes(id as string)) throw err("already-exists", "이미 받은 보상이에요", "Already claimed");
    if (progress < m.goal) throw err("failed-precondition", "아직 미션을 다 하지 않았어요", "Mission not complete yet");
    tx.update(ref, { coins: FieldValue.increment(m.coins), mClaimed: FieldValue.arrayUnion(id) });
    return { coins: m.coins };
  });
});

/**
 * 🎓 튜토리얼: 끝까지 했으면(completed) 계정당 한 번 💰 +20 · 💖 +1, 건너뛰었으면 다시 안 띄우게 표시만.
 * 보상을 받은 뒤 다시 해도 또 주지 않음.
 */
export const finishTutorial = onCall({ invoker: "public" }, async (req) => {
  const uid = requireUser(req);
  const completed = (req.data as { completed?: unknown } | undefined)?.completed === true;
  const ref = db.collection("users").doc(uid);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    const done = snap.get("tutorialDone") === true;
    if (completed && !done) {
      tx.update(ref, {
        tutorialDone: true, tutorialSeen: true,
        coins: FieldValue.increment(TUTORIAL_COINS), potions: FieldValue.increment(TUTORIAL_POTIONS),
      });
      return { reward: true, coins: TUTORIAL_COINS, potions: TUTORIAL_POTIONS };
    }
    tx.update(ref, { tutorialSeen: true });
    return { reward: false, coins: 0, potions: 0 };
  });
});

/** 주간 랭킹 보상 안내를 봤다고 표시 */
// 첫 배포 때 공개 호출 권한 설정이 실패했던 함수: 배포할 때마다 권한을 확실히 다시 설정하도록 명시
export const ackWeeklyReward = onCall({ invoker: "public" }, async (req) => {
  const uid = requireUser(req);
  await db.collection("users").doc(uid).update({ "weeklyReward.seen": true });
  return { ok: true };
});

/**
 * 매주 월요일 0시 5분(한국 시간): 랭킹 TOP 100에게 💰 상점 포인트 지급.
 * 1위 1000 · 2위 700 · 3위 500 · 4~10위 300 · 11~100위 100. weeklyRewards/{주}로 한 번만 지급.
 */
export const weeklyRankingRewards = onSchedule({ schedule: "5 0 * * 1", timeZone: "Asia/Seoul" }, async () => {
  // 지금은 새 주의 월요일 → 보상은 지난주 순위에 대한 것
  const week = weekKey(Date.now() - 86_400_000);
  const markRef = db.collection("weeklyRewards").doc(week);
  try {
    await markRef.create({ at: Date.now(), status: "running" });
  } catch {
    logger.info("weekly rewards already done", { week });
    return;
  }
  const top = await db.collection("users").orderBy("score", "desc").limit(100).get();
  const batch = db.batch();
  top.docs.forEach((d, i) => {
    const rank = i + 1;
    const coins = weeklyCoins(rank);
    if (coins <= 0) return;
    batch.update(d.ref, {
      coins: FieldValue.increment(coins),
      weeklyReward: { week, rank, coins, seen: false },
    });
  });
  batch.update(markRef, { status: "done", count: top.size });
  await batch.commit();
  logger.info("weekly rewards granted", { week, count: top.size });
});
