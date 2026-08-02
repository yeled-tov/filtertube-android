import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import test from "node:test";
import { normalizeCreemEvent, verifyCreemWebhook } from "./creem.js";

test("verifies the raw Creem webhook payload", () => {
  const body = Buffer.from('{"eventType":"subscription.paid"}');
  const secret = "webhook-secret";
  const signature = createHmac("sha256", secret).update(body).digest("hex");
  assert.equal(verifyCreemWebhook(body, signature, secret), true);
  assert.equal(verifyCreemWebhook(Buffer.from("tampered"), signature, secret), false);
  assert.equal(verifyCreemWebhook(body, "not-hex", secret), false);
});

test("normalizes a paid subscription for a configured product", () => {
  const normalized = normalizeCreemEvent({
    id: "evt_1",
    eventType: "subscription.paid",
    created_at: 1_728_734_327_355,
    object: {
      id: "sub_1",
      object: "subscription",
      status: "active",
      product: { id: "prod_month" },
      customer: { id: "cust_1", email: "User@Example.com" },
      metadata: { firebaseUid: "firebase-uid" },
      current_period_end_date: "2026-09-02T00:00:00.000Z",
    },
  }, { month: "prod_month", year: "prod_year" });
  assert.equal(normalized.active, true);
  assert.equal(normalized.plan, "month");
  assert.equal(normalized.firebaseUid, "firebase-uid");
  assert.equal(normalized.customerEmail, "user@example.com");
  assert.equal(normalized.subscriptionId, "sub_1");
});

test("never grants an unconfigured Creem product", () => {
  const normalized = normalizeCreemEvent({
    id: "evt_2",
    eventType: "checkout.completed",
    object: {
      id: "ch_1",
      object: "checkout",
      order: { status: "paid", product: "prod_other" },
      metadata: { firebaseUid: "firebase-uid" },
    },
  }, { month: "prod_month", year: "prod_year" });
  assert.equal(normalized.plan, null);
  assert.equal(normalized.active, false);
});

test("scheduled cancellation keeps access until period end", () => {
  const normalized = normalizeCreemEvent({
    id: "evt_3",
    eventType: "subscription.scheduled_cancel",
    object: {
      id: "sub_1",
      object: "subscription",
      product: { id: "prod_year" },
      current_period_end_date: "2026-12-01T00:00:00.000Z",
    },
  }, { month: "prod_month", year: "prod_year" });
  assert.equal(normalized.active, true);
  assert.equal(normalized.cancelAtPeriodEnd, true);
});
