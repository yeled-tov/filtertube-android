import { createHmac, timingSafeEqual } from "node:crypto";

const PRODUCTION_API_BASE = "https://api.creem.io";
const TEST_API_BASE = "https://test-api.creem.io";
const REQUEST_TIMEOUT_MS = 15_000;

function cleanBaseUrl(value) {
  const candidate = String(value || PRODUCTION_API_BASE).trim();
  if (candidate !== PRODUCTION_API_BASE && candidate !== TEST_API_BASE) {
    throw new Error("Unsupported Creem API base URL");
  }
  return candidate;
}

async function creemRequest({ apiKey, apiBase, path, method = "GET", body }) {
  if (!apiKey) throw new Error("CREEM_API_KEY is not configured");
  const response = await fetch(`${cleanBaseUrl(apiBase)}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "x-api-key": apiKey,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    const error = new Error("Creem API request failed");
    error.status = response.status;
    error.traceId = data?.trace_id || null;
    throw error;
  }
  return data;
}

export async function createCreemCheckout({
  apiKey,
  apiBase,
  productId,
  requestId,
  email,
  firebaseUid,
  plan,
  successUrl,
}) {
  const checkout = await creemRequest({
    apiKey,
    apiBase,
    path: "/v1/checkouts",
    method: "POST",
    body: {
      product_id: productId,
      request_id: requestId,
      units: 1,
      customer: { email },
      success_url: successUrl,
      metadata: {
        firebaseUid,
        plan,
        app: "filtertube-android",
      },
    },
  });
  if (!checkout?.id || !checkout?.checkout_url) {
    throw new Error("Creem did not return a checkout URL");
  }
  return checkout;
}

export async function createCreemCustomerPortal({
  apiKey,
  apiBase,
  customerId,
}) {
  const result = await creemRequest({
    apiKey,
    apiBase,
    path: "/v1/customers/billing",
    method: "POST",
    body: { customer_id: customerId },
  });
  if (!result?.customer_portal_link) {
    throw new Error("Creem did not return a customer portal URL");
  }
  return result.customer_portal_link;
}

export function verifyCreemWebhook(rawBody, signature, secret) {
  if (!signature || !secret || !/^[a-f\d]{64}$/i.test(signature)) return false;
  const expected = createHmac("sha256", secret).update(rawBody).digest();
  const received = Buffer.from(signature, "hex");
  return received.length === expected.length && timingSafeEqual(received, expected);
}

function objectId(value) {
  if (typeof value === "string") return value;
  return typeof value?.id === "string" ? value.id : null;
}

function epochSeconds(value) {
  if (!value) return null;
  const millis = Date.parse(value);
  return Number.isFinite(millis) ? Math.floor(millis / 1_000) : null;
}

function eventCreatedMillis(event) {
  const value = Number(event?.created_at);
  if (!Number.isFinite(value) || value <= 0) return Date.now();
  return value < 10_000_000_000 ? value * 1_000 : value;
}

function eventProductId(object) {
  return objectId(object?.product)
    || objectId(object?.order?.product)
    || object?.items?.find((item) => item?.product_id)?.product_id
    || objectId(object?.transaction?.product)
    || null;
}

function eventCustomer(object) {
  const candidates = [
    object?.customer,
    object?.order?.customer,
    object?.transaction?.customer,
    object?.subscription?.customer,
  ];
  const expanded = candidates.find(
    (candidate) => candidate && typeof candidate === "object",
  );
  const id = candidates.map(objectId).find(Boolean) || null;
  return {
    id,
    email: typeof expanded?.email === "string"
      ? expanded.email.trim().toLowerCase()
      : null,
  };
}

function eventMetadata(object) {
  const candidates = [
    object?.metadata,
    object?.subscription?.metadata,
    object?.order?.metadata,
  ];
  return candidates.find(
    (candidate) => candidate && typeof candidate === "object",
  ) || {};
}

function firebaseUidFromObject(object) {
  const metadata = eventMetadata(object);
  const value = metadata.firebaseUid || metadata.userId;
  return typeof value === "string" && value.length <= 128 ? value : null;
}

function planForProduct(productId, productIds) {
  if (productId && productId === productIds.month) return "month";
  if (productId && productId === productIds.year) return "year";
  return null;
}

function entitlementForEvent(eventType, object, plan) {
  if (!plan) return { active: false, status: "unmanaged" };
  if (eventType === "checkout.completed") {
    const paid = object?.order?.status === "paid";
    // A completed checkout establishes identity, but access is granted only by
    // a subscription lifecycle event. Creem explicitly recommends
    // subscription.paid for granting recurring access.
    return { active: false, status: paid ? "processing" : "pending" };
  }
  if (eventType === "subscription.scheduled_cancel") {
    return { active: true, status: "active" };
  }
  if ([
    "subscription.active",
    "subscription.paid",
    "subscription.trialing",
    "subscription.update",
  ].includes(eventType)) {
    const status = object?.status || (
      eventType === "subscription.trialing" ? "trialing" : "active"
    );
    return {
      active: status === "active" || status === "trialing",
      status,
    };
  }
  const inactiveStatus = {
    "subscription.canceled": "canceled",
    "subscription.expired": "expired",
    "subscription.unpaid": "unpaid",
    "subscription.past_due": "past_due",
    "subscription.paused": "paused",
    "refund.created": "refunded",
    "dispute.created": "disputed",
  }[eventType];
  return { active: false, status: inactiveStatus || "inactive" };
}

export function normalizeCreemEvent(event, productIds) {
  if (!event?.id || !event?.eventType || !event?.object) {
    throw new Error("Invalid Creem webhook event");
  }
  const object = event.object;
  const productId = eventProductId(object);
  const plan = planForProduct(productId, productIds);
  const entitlement = entitlementForEvent(event.eventType, object, plan);
  const customer = eventCustomer(object);
  const subscriptionId = objectId(object?.subscription)
    || (object?.object === "subscription" ? objectId(object) : null);
  const currentPeriodEnd = epochSeconds(
    object?.current_period_end_date
      || object?.subscription?.current_period_end_date,
  );
  return {
    eventId: event.id,
    eventType: event.eventType,
    eventCreatedMillis: eventCreatedMillis(event),
    firebaseUid: firebaseUidFromObject(object),
    productId,
    plan,
    active: entitlement.active,
    status: entitlement.status,
    cancelAtPeriodEnd: event.eventType === "subscription.scheduled_cancel",
    currentPeriodEnd,
    lastInvoiceStatus: {
      "checkout.completed": "paid",
      "subscription.paid": "paid",
      "subscription.unpaid": "unpaid",
      "subscription.past_due": "past_due",
      "refund.created": "refunded",
      "dispute.created": "disputed",
    }[event.eventType] || null,
    checkoutId: object?.object === "checkout" ? objectId(object) : null,
    subscriptionId,
    customerId: customer.id,
    customerEmail: customer.email,
  };
}
