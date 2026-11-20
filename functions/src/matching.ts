// 랜덤 매칭 상대 고르기 (순수 함수, 테스트용으로 분리)
// 비슷한 점수끼리 먼저 매칭하고, 오래 기다릴수록 허용 범위를 넓힙니다.

export interface Waiter {
  id: string;
  score: number;
  /** 대기열에 들어온 시각 (ms) */
  createdAt: number;
}

/** 이 시간이 지나면 점수와 상관없이 매칭 */
export const ANYONE_AFTER_MS = 60_000;

/** 기다린 시간에 따른 허용 점수 차: 처음 ±150, 1초마다 +25, 1분 지나면 무제한 */
export function scoreWindow(waitMs: number): number {
  if (waitMs >= ANYONE_AFTER_MS) return Number.POSITIVE_INFINITY;
  return 150 + Math.floor(Math.max(0, waitMs) / 1000) * 25;
}

/**
 * 나와 매칭할 상대. 두 사람 중 더 오래 기다린 쪽의 범위 안에 있으면 매칭 가능하고,
 * 그중 점수 차가 가장 작은 사람 (같으면 먼저 온 사람).
 */
export function pickOpponent(me: Waiter, candidates: Waiter[], now: number): Waiter | null {
  let best: Waiter | null = null;
  let bestDiff = Number.POSITIVE_INFINITY;
  for (const c of candidates) {
    if (c.id === me.id) continue;
    const diff = Math.abs(c.score - me.score);
    const allowed = Math.max(scoreWindow(now - me.createdAt), scoreWindow(now - c.createdAt));
    if (diff > allowed) continue;
    if (diff < bestDiff || (diff === bestDiff && best !== null && c.createdAt < best.createdAt)) {
      best = c;
      bestDiff = diff;
    }
  }
  return best;
}
