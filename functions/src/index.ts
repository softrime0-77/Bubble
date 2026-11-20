// Bubble 냥햄대전 서버.
// 매칭, 수 두기, 승패 판정, 점수 계산을 전부 서버에서 처리합니다.
// 앱은 games/queue 문서를 직접 쓸 수 없고(firestore.rules), 이 함수들을 호출만 합니다.

import { initializeApp } from "firebase-admin/app";
import { DocumentReference, DocumentSnapshot, FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { CallableRequest, HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { googleKeyLookup, verifySsv } from "./ssv";
import { AiLevel, chooseMove, isAiLevel } from "./ai";
import { ANIMALS, DEFAULT_TURN_MS, POTION_PRICE, rankBonus, revivesForGame, turnLimitAgainst } from "./catalog";
import { applyMove, Board, decodeMove, isLegal, MAX_SPLITS, opponent, Side, winner } from "./game";

initializeApp();
const db = getFirestore();

/** 오류: message는 한국어, details.en은 영어 (앱이 화면 언어에 맞춰 고름) */
function err(code: ConstructorParameters<typeof HttpsError>[0], ko: string, en: string): HttpsError {
  return new HttpsError(code, ko, { en });
}

setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });

const START_SCORE = 1000;
const WIN_POINTS = 25;
const LOSE_POINTS = 15;
const TURN_LIMIT_MS = DEFAULT_TURN_MS;
const TIMEOUT_GRACE_MS = 5_000;
const QUEUE_STALE_MS = 15_000;
/** 패배 후 보상형 광고를 보면 돌려주는 점수 (잃은 점수가 이보다 적으면 잃은 만큼만) */
const AD_REFUND_POINTS = 8;
/** AI 대전 점수 (난이도별 승리/패배). 승패 기록(wins/losses)에는 넣지 않음. */
const AI_POINTS: Record<AiLevel, { win: number; lose: number }> = {
  EASY: { win: 1, lose: 0 },
  NORMAL: { win: 5, lose: -3 },
  HARD: { win: 10, lose: -5 },
};
const AI_UID = "AI";
const AI_LABEL: Record<AiLevel, string> = { EASY: "쉬움", NORMAL: "보통", HARD: "어려움" };
const AI_THINK_MS = 900;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** 우리 보상형 전면 광고 단위 번호 (ca-app-pub-…/6715010091). 테스트 광고 단위는 거절. */
const REWARDED_AD_UNIT = "6715010091";

/** 구글로 로그인한 사용자만 허용합니다. */
function requireUser(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw err("unauthenticated", "로그인이 필요해요", "Please sign in");
  const provider = req.auth?.token.firebase?.sign_in_provider;
  if (provider !== "google.com") throw err("permission-denied", "구글 계정으로 로그인해 주세요", "Please sign in with Google");
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
    const delta = win === human ? pts.win + bonus : -Math.min(-pts.lose, score(humanSnap));
    tx.update(gameRef, {
      ...extra, status: "finished", winner: win, reason,
      [human === "CAT" ? "catDelta" : "hamDelta"]: delta,
      [human === "CAT" ? "catBonus" : "hamBonus"]: bonus,
    });
    if (delta !== 0) tx.update(humanSnap.ref, { score: FieldValue.increment(delta) });
    return;
  }
  // 이긴 쪽은 계급 보너스(구리 5% ~ 다이아 20%)를 더 받음
  const catBonus = catWon ? rankBonus(WIN_POINTS, score(users.cat)) : 0;
  const hamBonus = catWon ? 0 : rankBonus(WIN_POINTS, score(users.ham));
  const catDelta = catWon ? WIN_POINTS + catBonus : -Math.min(LOSE_POINTS, score(users.cat));
  const hamDelta = catWon ? -Math.min(LOSE_POINTS, score(users.ham)) : WIN_POINTS + hamBonus;
  tx.update(gameRef, { ...extra, status: "finished", winner: win, reason, catDelta, hamDelta, catBonus, hamBonus });
  tx.update(users.cat.ref, {
    score: FieldValue.increment(catDelta),
    [catWon ? "wins" : "losses"]: FieldValue.increment(1),
  });
  tx.update(users.ham.ref, {
    score: FieldValue.increment(hamDelta),
    [catWon ? "losses" : "wins"]: FieldValue.increment(1),
  });
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

    const waiting = await tx.get(db.collection("queue").where("status", "==", "waiting").limit(50));
    const candidates = waiting.docs
      .filter((d) => d.id !== uid && now - ((d.get("heartbeat") as number) ?? 0) < QUEUE_STALE_MS)
      .sort((a, b) => (a.get("createdAt") as number) - (b.get("createdAt") as number));
    const opp = candidates[0];
    const oppProfile = opp ? await tx.get(db.collection("users").doc(opp.id)) : null;

    const stillWaiting = me.exists && me.get("status") === "waiting";
    if (!opp || !oppProfile?.exists) {
      tx.set(meRef, {
        status: "waiting",
        heartbeat: now,
        createdAt: stillWaiting ? me.get("createdAt") : now,
        gameId: null,
      });
      return { gameId: null };
    }

    const gameRef = db.collection("games").doc();
    const iAmCat = Math.random() < 0.5;
    const [cat, ham] = iAmCat ? [myProfile, oppProfile] : [oppProfile, myProfile];
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
      catRevives: revivesForGame(cat.get("potions")),
      hamRevives: revivesForGame(ham.get("potions")),
      catSkin: cat.get("equipped") ?? null,
      hamSkin: ham.get("equipped") ?? null,
      catTurnMs: turnLimitAgainst(ham.get("equipped")),
      hamTurnMs: turnLimitAgainst(cat.get("equipped")),
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
    if (move.kind === "revive") {
      const mine = await tx.get(myRef);
      if (((mine.get("potions") as number | undefined) ?? 0) < 1) {
        throw err("failed-precondition", "부활물약이 없어요", "You have no revive potions");
      }
    }
    const r = applyMove(board, side, move);
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
      explodedSide: r.exploded?.side ?? null,
      explodedIndex: r.exploded?.index ?? -1,
      moveNo: game.moveNo + 1,
      turnStartedAt: Date.now(),
    };
    const win = winner(r.board);
    const players = win ? await readPlayers(tx, game) : null;
    if (move.kind === "revive") tx.update(myRef, { potions: FieldValue.increment(-1) });
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
    const r = applyMove(board, aiSide, chooseMove(board, aiSide, game.vsAi));
    const update = {
      cat: r.board.cat, ham: r.board.ham,
      catSplits: r.board.catSplits, hamSplits: r.board.hamSplits,
      catRevives: r.board.catRevives ?? 0, hamRevives: r.board.hamRevives ?? 0,
      turn: opponent(aiSide),
      lastDesc: r.description,
      lastDescEn: r.descriptionEn,
      explodedSide: r.exploded?.side ?? null,
      explodedIndex: r.exploded?.index ?? -1,
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
  if (!isAiLevel(level)) throw err("invalid-argument", "난이도를 골라 주세요", "Please choose a difficulty");
  const me = await db.collection("users").doc(uid).get();
  if (!me.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
  const humanIsCat = Math.random() < 0.5;
  const now = Date.now();
  const gameRef = db.collection("games").doc();
  const aiName = `AI 친구 (${AI_LABEL[level]})`;
  const myName = me.get("name") ?? "나";
  const myScore = me.get("score") ?? START_SCORE;
  const myRevives = revivesForGame(me.get("potions"));
  await gameRef.set({
    vsAi: level,
    catUid: humanIsCat ? uid : AI_UID,
    hamUid: humanIsCat ? AI_UID : uid,
    catName: humanIsCat ? myName : aiName,
    hamName: humanIsCat ? aiName : myName,
    catScore: humanIsCat ? myScore : 0,
    hamScore: humanIsCat ? 0 : myScore,
    catSkin: humanIsCat ? me.get("equipped") ?? null : null,
    hamSkin: humanIsCat ? null : me.get("equipped") ?? null,
    cat: [1, 1],
    ham: [1, 1],
    catSplits: MAX_SPLITS,
    hamSplits: MAX_SPLITS,
    catRevives: humanIsCat ? myRevives : 0,
    hamRevives: humanIsCat ? 0 : myRevives,
    catTurnMs: DEFAULT_TURN_MS,
    hamTurnMs: DEFAULT_TURN_MS,
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
    if (game.vsAi || game.status !== "playing" || game.turn !== opponent(side)) return false;
    const limit = (game.turn === "CAT" ? game.catTurnMs : game.hamTurnMs) ?? TURN_LIMIT_MS;
    if (Date.now() - game.turnStartedAt <= limit + TIMEOUT_GRACE_MS) return false;
    const players = await readPlayers(tx, game);
    finishGame(tx, ref, game, side, "timeout", players);
    return true;
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
  if (p.adUnit !== REWARDED_AD_UNIT) {
    logger.info("SSV for other ad unit ignored", { adUnit: p.adUnit });
    res.status(200).send("ignored");
    return;
  }
  const gameId = p.customData;
  if (!p.userId || !p.transactionId || !/^[A-Za-z0-9]{1,40}$/.test(gameId)) {
    res.status(200).send("ignored");
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
 * 상점 구매. 점수로 아이템을 삽니다.
 * - POTION: 부활물약 1개 (1000점)
 * - SHEEP / DOG / GIRAFFE: 동물 캐릭터 (사면 바로 장착)
 */
export const buyItem = onCall(async (req) => {
  const uid = requireUser(req);
  const item = (req.data as { item?: unknown } | null)?.item;
  if (typeof item !== "string" || (item !== "POTION" && !ANIMALS[item])) {
    throw err("invalid-argument", "없는 상품이에요", "No such item");
  }
  const price = item === "POTION" ? POTION_PRICE : ANIMALS[item].price;
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const u = await tx.get(userRef);
    if (!u.exists) throw err("failed-precondition", "프로필이 없어요", "Profile not found");
    const score = (u.get("score") as number | undefined) ?? START_SCORE;
    if (score < price) throw err("failed-precondition", `점수가 부족해요 (${price}점 필요)`, `Not enough points (${price} needed)`);
    const owned = (u.get("owned") as string[] | undefined) ?? [];
    if (item !== "POTION" && owned.includes(item)) throw err("already-exists", "이미 가지고 있어요", "You already own this");
    tx.update(
      userRef,
      item === "POTION"
        ? { score: FieldValue.increment(-price), potions: FieldValue.increment(1) }
        : { score: FieldValue.increment(-price), owned: FieldValue.arrayUnion(item), equipped: item },
    );
  });
  return { ok: true };
});

/** 가진 동물 캐릭터 장착 (null이면 기본 고양이/햄스터). 다음 판부터 적용됩니다. */
export const equipAnimal = onCall(async (req) => {
  const uid = requireUser(req);
  const animal = (req.data as { animal?: unknown } | null)?.animal ?? null;
  if (animal !== null && (typeof animal !== "string" || !ANIMALS[animal])) {
    throw err("invalid-argument", "없는 캐릭터예요", "No such character");
  }
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const u = await tx.get(userRef);
    const owned = (u.get("owned") as string[] | undefined) ?? [];
    if (animal !== null && !owned.includes(animal as string)) {
      throw err("failed-precondition", "아직 없는 캐릭터예요", "You don't own this character yet");
    }
    tx.update(userRef, { equipped: animal });
  });
  return { ok: true };
});
