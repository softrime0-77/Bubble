import { test } from "node:test";
import assert from "node:assert/strict";
import { dayKey, friendDeltas, FRIEND_HOST_STALE_MS, FRIEND_ROOM_TTL_MS, isRated, pairKey, roomJoinable } from "../src/friend";

test("pairKey ignores order", () => {
  assert.equal(pairKey("b", "a"), "a_b");
  assert.equal(pairKey("a", "b"), "a_b");
});

test("dayKey uses Korean time", () => {
  // 2026-01-01 15:00 UTC = 2026-01-02 00:00 KST
  assert.equal(dayKey(Date.UTC(2026, 0, 1, 15, 0)), "2026-01-02");
  assert.equal(dayKey(Date.UTC(2026, 0, 1, 14, 59)), "2026-01-01");
});

test("first five games a day are rated", () => {
  assert.equal(isRated(0), true);
  assert.equal(isRated(4), true);
  assert.equal(isRated(5), false);
});

test("friend deltas: +30 / -10, never below zero, nothing when unrated", () => {
  assert.deepEqual(friendDeltas(true, 1000), { win: 30, lose: -10 });
  assert.deepEqual(friendDeltas(true, 4), { win: 30, lose: -4 });
  assert.deepEqual(friendDeltas(true, 0), { win: 30, lose: 0 });
  assert.deepEqual(friendDeltas(false, 1000), { win: 0, lose: 0 });
});

test("room is joinable only while waiting, fresh and not expired", () => {
  const now = 1_000_000_000;
  assert.equal(roomJoinable({ status: "waiting", createdAt: now - 1000, heartbeat: now - 1000 }, now), true);
  assert.equal(roomJoinable({ status: "matched", createdAt: now, heartbeat: now }, now), false);
  assert.equal(roomJoinable({ status: "waiting", createdAt: now - FRIEND_ROOM_TTL_MS, heartbeat: now }, now), false);
  assert.equal(roomJoinable({ status: "waiting", createdAt: now, heartbeat: now - FRIEND_HOST_STALE_MS }, now), false);
});
