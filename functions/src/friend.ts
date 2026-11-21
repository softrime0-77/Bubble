// 친구 대전 (초대 링크로 같은 방에 들어와 1:1). 순수 함수만 모아 테스트하기 쉽게 분리.

/** 친구 대전 점수: 이기면 +30, 지면 -10 (0점 밑으로는 안 내려감). 계급 보너스·부스터 없음 */
export const FRIEND_WIN_POINTS = 30;
export const FRIEND_LOSE_POINTS = 10;
/** 같은 두 사람은 하루에 이 판 수까지만 점수가 걸림 (그 뒤로는 점수 없는 친선전) */
export const FRIEND_RATED_PER_DAY = 5;
/** 방을 만든 뒤 친구를 기다리는 최대 시간 */
export const FRIEND_ROOM_TTL_MS = 10 * 60_000;
/** 방장이 이 시간 동안 확인 신호를 안 보내면 나간 것으로 봄 */
export const FRIEND_HOST_STALE_MS = 20_000;

/** 두 사람 순서와 상관없는 쌍 키 */
export function pairKey(a: string, b: string): string {
  return a < b ? `${a}_${b}` : `${b}_${a}`;
}

/** 하루 제한에 쓰는 날짜 (한국 시간 기준 YYYY-MM-DD) */
export function dayKey(now: number): string {
  return new Date(now + 9 * 3600_000).toISOString().slice(0, 10);
}

/** 오늘 이미 점수 걸린 판을 [playedToday]판 했을 때 이번 판이 점수 걸린 판인지 */
export function isRated(playedToday: number): boolean {
  return playedToday < FRIEND_RATED_PER_DAY;
}

/** 친구 대전 점수 변화 (이긴 쪽, 진 쪽). 점수 없는 판이면 둘 다 0 */
export function friendDeltas(rated: boolean, loserScore: number): { win: number; lose: number } {
  if (!rated) return { win: 0, lose: 0 };
  const lost = Math.min(FRIEND_LOSE_POINTS, Math.max(0, loserScore));
  return { win: FRIEND_WIN_POINTS, lose: lost === 0 ? 0 : -lost };
}

/** 방이 아직 들어갈 수 있는 상태인지 */
export function roomJoinable(
  room: { status?: unknown; createdAt?: unknown; heartbeat?: unknown },
  now: number,
): boolean {
  const created = typeof room.createdAt === "number" ? room.createdAt : 0;
  const beat = typeof room.heartbeat === "number" ? room.heartbeat : 0;
  return room.status === "waiting" && now - created < FRIEND_ROOM_TTL_MS && now - beat < FRIEND_HOST_STALE_MS;
}
