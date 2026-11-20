import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import { verifySsv } from "../src/ssv";

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const lookup = async (id: string) => (id === "123" ? publicKey : null);

function signed(message: string, keyId = "123"): string {
  const full = `${message}&key_id=${keyId}`;
  const sig = sign("sha256", Buffer.from(full), { key: privateKey, dsaEncoding: "der" })
    .toString("base64url");
  return `${full}&signature=${sig}`;
}

const base = "ad_network=5450213213286189855&ad_unit=6715010091&custom_data=game123&reward_amount=1&reward_item=Reward&timestamp=1700000000000&transaction_id=abc&user_id=uid42";

test("valid signature returns params", async () => {
  const p = await verifySsv(signed(base), lookup);
  assert.ok(p);
  assert.equal(p!.adUnit, "6715010091");
  assert.equal(p!.customData, "game123");
  assert.equal(p!.userId, "uid42");
  assert.equal(p!.transactionId, "abc");
});

test("tampered query is rejected", async () => {
  const q = signed(base).replace("user_id=uid42", "user_id=evil");
  assert.equal(await verifySsv(q, lookup), null);
});

test("unknown key or missing signature is rejected", async () => {
  assert.equal(await verifySsv(signed(base, "999"), lookup), null);
  assert.equal(await verifySsv(base + "&key_id=123", lookup), null);
});
