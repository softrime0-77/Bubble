import { test } from "node:test";
import assert from "node:assert/strict";
import { LAND_PRICE, landCheck } from "../src/catalog";

test("land costs 100,000 score and can be bought once", () => {
  assert.equal(LAND_PRICE, 100_000);
  assert.equal(landCheck(undefined, 100_000), "ok");
  assert.equal(landCheck(false, 250_000), "ok");
  assert.equal(landCheck(undefined, 99_999), "short");
  assert.equal(landCheck(true, 500_000), "owned");
});
