// Bubble 냥햄대전 서버.
// 매칭, 수 두기, 승패 판정, 점수 계산을 전부 서버에서 처리합니다.
// 앱은 games/queue 문서를 직접 쓸 수 없고(firestore.rules), 이 함수들을 호출만 합니다.

import { initializeApp } from "firebase-admin/app";
import { DocumentReference, DocumentSnapshot, FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { applyMove, Board, decodeMove, isLegal, MAX_SPLITS, opponent, Side, winner } from "./game";

initializeApp();
const db = getFirestore();

setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });

const START_SCORE = 1000;
const WIN_POINTS = 25;
const LOSE_POINTS = 15;
const TURN_LIMIT_MS = 30_000;
const TIMEOUT_GRACE_MS = 5_000;
const QUEUE_STALE_MS = 15_000;

/** 구글로 로그인한 사용자만 허용합니다. */
function requireUser(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "로그인이 필요해요");
  const provider = req.auth?.token.firebase?.sign_in_provider;
  if (provider !== "google.com") throw new HttpsError("permission-denied", "구글 계정으로 로그인해 주세요");
  return uid;
}

function requireGameId(data: unknown): string {
  const id = (data as { gameId?: unknown } | null)?.gameId;
  if (typeof id !== "string" || !/^[A-Za-z0-9]{1,40}$/.test(id)) {
    throw new HttpsError("invalid-argument", "잘못된 게임이에요");
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
  turn: Side;
  status: "playing" | "finished";
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
  if (!snap.exists) throw new HttpsError("not-found", "게임을 찾을 수 없어요");
  const game = snap.data() as Game;
  const side = sideOf(game, uid);
  if (!side) throw new HttpsError("permission-denied", "내 게임이 아니에요");
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
  const catDelta = catWon ? WIN_POINTS : -Math.min(LOSE_POINTS, score(users.cat));
  const hamDelta = catWon ? -Math.min(LOSE_POINTS, score(users.ham)) : WIN_POINTS;
  tx.update(gameRef, { ...extra, status: "finished", winner: win, reason, catDelta, hamDelta });
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
    if (!myProfile.exists) throw new HttpsError("failed-precondition", "프로필이 없어요");

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
  if (!move) throw new HttpsError("invalid-argument", "잘못된 수예요");

  await db.runTransaction(async (tx) => {
    const { ref, game, side } = await loadGame(tx, gameId, uid);
    if (game.status !== "playing") throw new HttpsError("failed-precondition", "이미 끝난 게임이에요");
    if (game.turn !== side) throw new HttpsError("failed-precondition", "내 차례가 아니에요");
    const board: Board = {
      cat: game.cat,
      ham: game.ham,
      catSplits: game.catSplits ?? MAX_SPLITS,
      hamSplits: game.hamSplits ?? MAX_SPLITS,
    };
    if (!isLegal(board, side, move)) throw new HttpsError("invalid-argument", "규칙에 맞지 않는 수예요");

    const r = applyMove(board, side, move);
    const update = {
      cat: r.board.cat,
      ham: r.board.ham,
      catSplits: r.board.catSplits,
      hamSplits: r.board.hamSplits,
      turn: opponent(side),
      lastDesc: r.description,
      explodedSide: r.exploded?.side ?? null,
      explodedIndex: r.exploded?.index ?? -1,
      moveNo: game.moveNo + 1,
      turnStartedAt: Date.now(),
    };
    const win = winner(r.board);
    if (win) {
      const players = await readPlayers(tx, game);
      finishGame(tx, ref, game, win, "normal", players, update);
    } else {
      tx.update(ref, update);
    }
  });
  return { ok: true };
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
    if (game.status !== "playing" || game.turn !== opponent(side)) return false;
    if (Date.now() - game.turnStartedAt <= TURN_LIMIT_MS + TIMEOUT_GRACE_MS) return false;
    const players = await readPlayers(tx, game);
    finishGame(tx, ref, game, side, "timeout", players);
    return true;
  });
  return { won };
});
