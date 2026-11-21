import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, sign } from "node:crypto";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { appAccountToken, verifyAppleJws } from "../src/apple";

// 테스트용 가짜 인증서 체인 (Apple 체인과 같은 모양, Apple OID 포함). test/apple-fixture.json
const fx = JSON.parse(readFileSync(join(__dirname, "../../test/apple-fixture.json"), "utf8")) as {
  root: string; intermediate: string; leaf: string; leafKey: string;
};
const rootSha = createHash("sha256").update(Buffer.from(fx.root, "base64")).digest("hex");
const enc = (o: unknown) => Buffer.from(JSON.stringify(o)).toString("base64url");

function makeJws(payload: object, chain = [fx.leaf, fx.intermediate, fx.root]): string {
  const head = enc({ alg: "ES256", x5c: chain });
  const body = enc(payload);
  const sig = sign("sha256", Buffer.from(`${head}.${body}`), { key: fx.leafKey, dsaEncoding: "ieee-p1363" });
  return `${head}.${body}.${sig.toString("base64url")}`;
}

const tx = { transactionId: "2000000123", bundleId: "com.bubble.game", productId: "points_500", type: "Consumable", signedDate: Date.now() };

test("valid chain and signature is accepted", () => {
  const r = verifyAppleJws(makeJws(tx), rootSha);
  assert.equal(r?.transactionId, "2000000123");
  assert.equal(r?.productId, "points_500");
});

test("other root (e.g. the real Apple pin) rejects test chain", () => {
  assert.equal(verifyAppleJws(makeJws(tx)), null);
});

test("tampered payload is rejected", () => {
  const [h, , s] = makeJws(tx).split(".");
  const forged = `${h}.${enc({ ...tx, productId: "points_2000" })}.${s}`;
  assert.equal(verifyAppleJws(forged, rootSha), null);
});

test("broken chain order is rejected", () => {
  assert.equal(verifyAppleJws(makeJws(tx, [fx.intermediate, fx.leaf, fx.root]), rootSha), null);
  assert.equal(verifyAppleJws(makeJws(tx, [fx.leaf, fx.root, fx.root]), rootSha), null);
});

test("garbage is rejected", () => {
  assert.equal(verifyAppleJws("abc", rootSha), null);
  assert.equal(verifyAppleJws("a.b.c", rootSha), null);
  assert.equal(verifyAppleJws(undefined as unknown as string, rootSha), null);
});

test("app account token is a stable UUID per uid", () => {
  const a = appAccountToken("user-1");
  assert.match(a, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(a, appAccountToken("user-1"));
  assert.notEqual(a, appAccountToken("user-2"));
});
