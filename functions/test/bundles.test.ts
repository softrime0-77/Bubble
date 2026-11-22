import { test } from "node:test";
import assert from "node:assert/strict";
import { ANIMALS, BUNDLES, bundlePrice, POTION_PRICE } from "../src/catalog";

test("animal prices and bundles", () => {
  assert.deepEqual([ANIMALS.SHEEP.price, ANIMALS.DOG.price, ANIMALS.GIRAFFE.price], [20, 40, 70]);
  assert.equal(bundlePrice("SHEEP", 3), 50);
  assert.equal(bundlePrice("DOG", 3), 100);
  assert.equal(bundlePrice("GIRAFFE", 3), 180);
  assert.equal(bundlePrice("GIRAFFE", 2), null);
});

test("potion bundles up to 10 with discounts", () => {
  assert.equal(bundlePrice("POTION", 1), POTION_PRICE);
  assert.equal(bundlePrice("POTION", 3), 135);
  assert.equal(bundlePrice("POTION", 5), 200);
  assert.equal(bundlePrice("POTION", 10), 350);
  assert.equal(bundlePrice("POTION", 11), null);
  assert.equal(bundlePrice("POTION", "3"), null);
  assert.equal(bundlePrice("POTION", 2.5), null);
});

test("bundles are always cheaper per item than buying one by one", () => {
  for (const [item, table] of Object.entries(BUNDLES)) {
    const one = table[1];
    for (const [q, price] of Object.entries(table)) {
      assert.ok(price <= one * Number(q), `${item} x${q}`);
    }
  }
});
