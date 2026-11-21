// 출석 보상 · 일일 미션 · 주간 랭킹 보상 · 광고 보고 물약 (순수 함수, 테스트용으로 분리)
// 보상은 모두 💰 상점 포인트(coins)로 줌 → 계급·랭킹 점수에는 영향 없음

import { dayKey } from "./friend";

/** 출석 7일 주기 보상 (💰). 7일째는 💖 부활물약 1개도 같이 */
export const CHECKIN_COINS = [20, 20, 30, 30, 50, 50, 100];
export const CHECKIN_POTION_DAY = 7;

/** 한국 시간 날짜(YYYY-MM-DD)의 하루 전 */
export function prevDay(day: string): string {
  const t = Date.parse(`${day}T00:00:00Z`) - 86_400_000;
  return new Date(t).toISOString().slice(0, 10);
}

/**
 * 오늘 출석하면 몇 일째가 되는지 (1~7). 이미 오늘 받았으면 null.
 * 어제 받았으면 이어서(7일 다음은 다시 1일), 아니면 1일부터.
 */
export function nextStreak(lastDay: unknown, streak: unknown, today: string): number | null {
  if (lastDay === today) return null;
  const s = typeof streak === "number" && streak >= 1 && streak <= 7 ? streak : 0;
  return lastDay === prevDay(today) ? (s % 7) + 1 : 1;
}

/** 일일 미션 (한국 시간 하루 기준). field는 users 문서의 진행도 필드 */
export const MISSIONS: Record<string, { goal: number; field: "mPlay" | "mWin" | "mRandom"; coins: number }> = {
  PLAY3: { goal: 3, field: "mPlay", coins: 20 },
  WIN2: { goal: 2, field: "mWin", coins: 30 },
  RANDOM1: { goal: 1, field: "mRandom", coins: 20 },
};

/**
 * 대전이 끝났을 때 미션 진행도 갱신 필드. 날짜가 바뀌었으면 0부터 새로 셈.
 * [user]는 끝나기 전의 users 문서 값.
 */
export function missionProgress(
  user: { missionDay?: unknown; mPlay?: unknown; mWin?: unknown; mRandom?: unknown },
  today: string,
  inc: { play: number; win: number; random: number },
): Record<string, unknown> {
  const same = user.missionDay === today;
  const n = (v: unknown) => (same && typeof v === "number" ? v : 0);
  return {
    missionDay: today,
    mPlay: n(user.mPlay) + inc.play,
    mWin: n(user.mWin) + inc.win,
    mRandom: n(user.mRandom) + inc.random,
    ...(same ? {} : { mClaimed: [] }),
  };
}

/** 주간 랭킹 보상 (💰): 1위 1000 · 2위 700 · 3위 500 · 4~10위 300 · 11~100위 100 */
export function weeklyCoins(rank: number): number {
  if (rank === 1) return 1000;
  if (rank === 2) return 700;
  if (rank === 3) return 500;
  if (rank >= 4 && rank <= 10) return 300;
  if (rank >= 11 && rank <= 100) return 100;
  return 0;
}

/** 주 표시 (한국 시간, ISO 주): 예 "2026-W47" */
export function weekKey(now: number): string {
  const d = new Date(Date.parse(`${dayKey(now)}T00:00:00Z`));
  const dow = (d.getUTCDay() + 6) % 7; // 월요일 0
  d.setUTCDate(d.getUTCDate() - dow + 3); // 그 주의 목요일
  const year = d.getUTCFullYear();
  const week = Math.ceil(((d.getTime() - Date.UTC(year, 0, 1)) / 86_400_000 + 1) / 7);
  return `${year}-W${String(week).padStart(2, "0")}`;
}

/** 광고 보고 💖 부활물약: 하루 최대 횟수 */
export const POTION_ADS_PER_DAY = 3;

/** 오늘 광고로 받은 물약 수 */
export function potionAdsToday(user: { potionAdDay?: unknown; potionAds?: unknown }, today: string): number {
  return user.potionAdDay === today && typeof user.potionAds === "number" ? user.potionAds : 0;
}
