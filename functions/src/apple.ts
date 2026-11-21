// App Store 결제 확인 (StoreKit 2).
// 아이폰 앱은 결제 후 Apple이 서명한 거래 정보(JWS, transaction.jwsRepresentation)를 보냅니다.
// 서버는 그 서명이 Apple 인증서 체인(Apple Root CA - G3)으로 이어지는지 직접 확인하므로
// App Store Connect API 키 없이도 진짜 결제인지 알 수 있습니다.

import { createHash, verify as verifySignature, X509Certificate } from "node:crypto";

/** Apple Root CA - G3 인증서의 SHA-256 지문 (https://www.apple.com/certificateauthority/AppleRootCA-G3.cer). CI에서 다시 확인함. */
export const APPLE_ROOT_G3_SHA256 = "63343ABFB89A6A03EBB57E9B3F5FA7BE7C4F5C756F3017B3A8C488C3653E9179";

/** Apple 인증서 표시(OID): 중간 인증서 1.2.840.113635.100.6.2.1, 서명 인증서 1.2.840.113635.100.6.11.1 */
const OID_INTERMEDIATE = Buffer.from("060a2a864886f76364060201", "hex");
const OID_LEAF = Buffer.from("060a2a864886f76364060b01", "hex");

export interface AppleTransaction {
  transactionId: string;
  originalTransactionId?: string;
  bundleId: string;
  productId: string;
  type?: string;
  environment?: string;
  appAccountToken?: string;
  purchaseDate?: number;
  signedDate?: number;
  revocationDate?: number;
  quantity?: number;
}

function b64url(s: string): Buffer {
  return Buffer.from(s.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

function fingerprint(c: X509Certificate): string {
  return createHash("sha256").update(c.raw).digest("hex").toUpperCase();
}

/**
 * StoreKit 2 거래 JWS를 확인하고 내용을 돌려줍니다. 서명이 틀리면 null.
 * rootSha256은 테스트에서만 바꿉니다.
 */
export function verifyAppleJws(jws: string, rootSha256: string = APPLE_ROOT_G3_SHA256): AppleTransaction | null {
  const parts = typeof jws === "string" ? jws.split(".") : [];
  if (parts.length !== 3 || jws.length > 20_000) return null;
  try {
    const header = JSON.parse(b64url(parts[0]).toString("utf8")) as { alg?: string; x5c?: string[] };
    if (header.alg !== "ES256" || !Array.isArray(header.x5c) || header.x5c.length !== 3) return null;
    const [leaf, intermediate, root] = header.x5c.map((c) => new X509Certificate(Buffer.from(c, "base64")));

    // 인증서 체인: 서명 인증서 ← 중간 인증서 ← Apple 루트 (루트는 지문으로 고정)
    if (fingerprint(root) !== rootSha256.toUpperCase()) return null;
    if (!root.verify(root.publicKey) || !intermediate.checkIssued(root) || !intermediate.verify(root.publicKey)) return null;
    if (!leaf.checkIssued(intermediate) || !leaf.verify(intermediate.publicKey)) return null;
    if (!intermediate.raw.includes(OID_INTERMEDIATE) || !leaf.raw.includes(OID_LEAF)) return null;

    // 거래 정보 서명
    const ok = verifySignature(
      "sha256",
      Buffer.from(`${parts[0]}.${parts[1]}`),
      { key: leaf.publicKey, dsaEncoding: "ieee-p1363" },
      b64url(parts[2]),
    );
    if (!ok) return null;

    const payload = JSON.parse(b64url(parts[1]).toString("utf8")) as AppleTransaction;
    if (typeof payload.transactionId !== "string" || typeof payload.productId !== "string") return null;
    // 서명 시각에 인증서가 유효했는지
    const at = new Date(payload.signedDate ?? Date.now());
    for (const c of [leaf, intermediate]) {
      if (at < new Date(c.validFrom) || at > new Date(c.validTo)) return null;
    }
    return payload;
  } catch {
    return null;
  }
}

/**
 * 결제를 계정에 묶는 표시 (StoreKit appAccountToken, UUID 모양).
 * 앱과 같은 방법으로 uid에서 만듭니다: SHA-256(uid) 앞 16바이트를 UUID v4 형식으로.
 */
export function appAccountToken(uid: string): string {
  const b = createHash("sha256").update(uid).digest().subarray(0, 16);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = b.toString("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
