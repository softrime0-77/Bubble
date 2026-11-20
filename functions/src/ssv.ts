// AdMob 보상형 광고 서버 측 확인(SSV) 서명 검증.
// https://developers.google.com/admob/android/ssv
//
// AdMob은 광고를 끝까지 본 사용자가 있으면 우리 콜백 URL로 GET 요청을 보냅니다.
// 쿼리 문자열 중 "&signature=" 앞부분 전체가 서명 대상이고, 서명은 key_id에 해당하는
// 구글 공개키(ECDSA P-256, SHA-256)로 검증합니다.

import { createPublicKey, verify, KeyObject } from "node:crypto";

export const VERIFIER_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";

export interface SsvParams {
  adUnit: string;
  customData: string;
  userId: string;
  transactionId: string;
  timestampMs: number;
  rewardAmount: number;
  rewardItem: string;
}

export type KeyLookup = (keyId: string) => Promise<KeyObject | null>;

function base64UrlDecode(s: string): Buffer {
  return Buffer.from(s.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

/**
 * 원본 쿼리 문자열(앞의 '?' 제외)을 검증하고 파라미터를 돌려줍니다.
 * 서명이 틀리면 null.
 */
export async function verifySsv(rawQuery: string, lookupKey: KeyLookup): Promise<SsvParams | null> {
  const marker = "&signature=";
  const at = rawQuery.indexOf(marker);
  if (at < 0) return null;
  const message = rawQuery.slice(0, at);
  const params = new URLSearchParams(rawQuery);
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  if (!signature || !keyId) return null;

  const key = await lookupKey(keyId);
  if (!key) return null;
  const ok = verify("sha256", Buffer.from(message, "utf8"), { key, dsaEncoding: "der" }, base64UrlDecode(signature));
  if (!ok) return null;

  return {
    adUnit: params.get("ad_unit") ?? "",
    customData: params.get("custom_data") ?? "",
    userId: params.get("user_id") ?? "",
    transactionId: params.get("transaction_id") ?? "",
    timestampMs: Number(params.get("timestamp") ?? "0"),
    rewardAmount: Number(params.get("reward_amount") ?? "0"),
    rewardItem: params.get("reward_item") ?? "",
  };
}

/** 구글 공개키 목록을 받아 와서 하루 동안 캐시합니다. */
export function googleKeyLookup(fetchJson: (url: string) => Promise<unknown> = defaultFetch): KeyLookup {
  let cache: { at: number; keys: Map<string, KeyObject> } | null = null;
  return async (keyId) => {
    const fresh = cache && Date.now() - cache.at < 24 * 3600_000;
    if (!fresh || !cache!.keys.has(keyId)) {
      const json = (await fetchJson(VERIFIER_KEYS_URL)) as { keys?: { keyId: number | string; pem: string }[] };
      const keys = new Map<string, KeyObject>();
      for (const k of json.keys ?? []) keys.set(String(k.keyId), createPublicKey(k.pem));
      cache = { at: Date.now(), keys };
    }
    return cache!.keys.get(keyId) ?? null;
  };
}

async function defaultFetch(url: string): Promise<unknown> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`verifier keys HTTP ${res.status}`);
  return res.json();
}
