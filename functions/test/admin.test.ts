import { test } from "node:test";
import assert from "node:assert/strict";
import { ADMIN_MAX_VALUE, adminValue } from "../src/catalog";

test("admin values: whole numbers 0..10M, missing means unchanged", () => {
  assert.equal(adminValue(undefined), undefined);
  assert.equal(adminValue(null), undefined);
  assert.equal(adminValue(0), 0);
  assert.equal(adminValue(5000), 5000);
  assert.equal(adminValue(ADMIN_MAX_VALUE), ADMIN_MAX_VALUE);
  assert.equal(adminValue(ADMIN_MAX_VALUE + 1), null);
  assert.equal(adminValue(-1), null);
  assert.equal(adminValue(1.5), null);
  assert.equal(adminValue("100"), null);
});
