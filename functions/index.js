import { onRequest } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";
import Stripe from "stripe";

if (getApps().length === 0) initializeApp();

const auth = getAuth();
const db = getFirestore();
const stripeSecret = defineSecret("STRIPE_SECRET_KEY");
const webhookSecret = defineSecret("STRIPE_WEBHOOK_SECRET");
// Price IDs are deployment parameters with no code default. This prevents a
// Live secret from ever being paired silently with a Test-mode Price.
const monthlyPrice = defineString("STRIPE_MONTHLY_PRICE_ID");
const yearlyPrice = defineString("STRIPE_YEARLY_PRICE_ID");
const successUrl = defineString("STRIPE_SUCCESS_URL", {
  default: "https://filter-tube-52d8e.web.app/premium/success?session_id={CHECKOUT_SESSION_ID}",
});
const cancelUrl = defineString("STRIPE_CANCEL_URL", {
  default: "https://filter-tube-52d8e.web.app/premium/cancelled",
});
const portalReturnUrl = defineString("STRIPE_PORTAL_RETURN_URL", {
  default: "https://filter-tube-52d8e.web.app/premium/success",
});

const REGION = "europe-west1";
const STRIPE_API_VERSION = "2025-06-30.basil";
const CHECKOUT_LOCK_MS = 90_000;
const CHECKOUT_COOLDOWN_MS = 15_000;
const STATUS_REFRESH_COOLDOWN_MS = 15_000;
const STATUS_REFRESH_LOCK_MS = 30_000;
const PORTAL_COOLDOWN_MS = 15_000;
const PORTAL_LOCK_MS = 30_000;
const CHANNEL_REQUEST_COOLDOWN_MS = 5 * 60 * 1000;
const BUG_REPORT_COOLDOWN_MS = 10 * 60 * 1000;
const BILLING_RECONCILE_MAX_ATTEMPTS = 3;
const TRIAL_DURATION_MS = 30 * 24 * 60 * 60 * 1000;
const CHANNEL_REQUEST_CATEGORIES = new Set([
  "torah",
  "torah_study",
  "music",
  "dati_light",
  "kids",
  "diy",
  "cooking",
  "beauty",
  "fashion",
  "home",
  "education",
  "events",
  "news",
  "general",
]);
const PAYMENT_HTTP_OPTIONS = {
  region: REGION,
  maxInstances: 10,
  concurrency: 20,
  timeoutSeconds: 30,
};
const STATUS_HTTP_OPTIONS = {
  region: REGION,
  maxInstances: 10,
  concurrency: 40,
  timeoutSeconds: 30,
};
const WEBHOOK_HTTP_OPTIONS = {
  region: REGION,
  maxInstances: 10,
  concurrency: 40,
  timeoutSeconds: 60,
};
const TRUSTED_WEB_ORIGINS = new Set([
  "https://filter-tube-52d8e.web.app",
  "https://filter-tube-52d8e.firebaseapp.com",
]);

function handleCors(req, res) {
  const origin = req.get("origin");
  // Android's HTTP client has no Origin header. Browser calls are restricted
  // to this Firebase Hosting project.
  if (origin && !TRUSTED_WEB_ORIGINS.has(origin)) {
    res.status(403).json({ ok: false, message: "Origin is not allowed" });
    return true;
  }
  if (origin) {
    res.set("Access-Control-Allow-Origin", origin);
    res.set("Vary", "Origin");
    res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
  }
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return true;
  }
  return false;
}

async function requireUser(req, res, checkRevoked = false) {
  const header = req.get("authorization") || "";
  if (!header.startsWith("Bearer ")) {
    res.status(401).json({ ok: false, message: "Authentication required" });
    return null;
  }
  try {
    return await auth.verifyIdToken(
      header.substring("Bearer ".length),
      checkRevoked,
    );
  } catch {
    res.status(401).json({ ok: false, message: "Invalid authentication token" });
    return null;
  }
}

function requireVerifiedEmail(decoded, res) {
  if (decoded.email_verified === true) return true;
  res.status(403).json({
    ok: false,
    code: "EMAIL_NOT_VERIFIED",
    message: "Email verification is required",
  });
  return false;
}

function requireAdmin(decoded, res) {
  if (decoded.admin === true) return true;
  res.status(403).json({
    ok: false,
    code: "ADMIN_REQUIRED",
    message: "Administrator access is required",
  });
  return false;
}

function stripeClient() {
  return new Stripe(stripeSecret.value(), { apiVersion: STRIPE_API_VERSION });
}

function priceFor(plan) {
  if (plan === "month") return monthlyPrice.value();
  if (plan === "year") return yearlyPrice.value();
  return null;
}

function normalizePlan(plan) {
  return plan === "month" || plan === "year" ? plan : null;
}

function stripeId(value) {
  return typeof value === "string" ? value : value?.id ?? null;
}

function planForPriceId(priceId) {
  if (priceId === monthlyPrice.value()) return "month";
  if (priceId === yearlyPrice.value()) return "year";
  return null;
}

function planFromSubscription(subscription) {
  for (const item of subscription?.items?.data || []) {
    const plan = planForPriceId(stripeId(item?.price));
    if (plan) return plan;
  }
  return null;
}

function subscriptionPeriodEnd(subscription) {
  const knownPriceItem = subscription?.items?.data?.find(
    (item) => planForPriceId(stripeId(item?.price)),
  );
  return knownPriceItem?.current_period_end
    ?? subscription?.current_period_end
    ?? subscription?.items?.data?.[0]?.current_period_end
    ?? null;
}

function invoiceSubscriptionId(invoice) {
  return stripeId(
    invoice?.parent?.subscription_details?.subscription
      ?? invoice?.subscription,
  );
}

function entitledStatus(status) {
  return status === "active" || status === "trialing";
}

function expandedLatestInvoice(subscription) {
  const invoice = subscription?.latest_invoice;
  return invoice && typeof invoice === "object" ? invoice : null;
}

function subscriptionPaymentSatisfied(subscription) {
  const invoice = expandedLatestInvoice(subscription);
  return Boolean(invoice)
    && (invoice.paid === true || invoice.status === "paid");
}

function subscriptionEntitled(subscription) {
  return entitledStatus(subscription?.status)
    && subscriptionPaymentSatisfied(subscription);
}

function managedSubscriptionPlan(subscription) {
  return planFromSubscription(subscription);
}

function managedBillingEntitled(billing = {}) {
  return Boolean(billing.manualPremiumActive)
    || (
      Boolean(normalizePlan(billing.plan))
      && Boolean(billing.active)
    );
}

function billingRef(uid) {
  return db.collection("users").doc(uid).collection("billing").doc("status");
}

function stripeCustomerRef(customerId) {
  return db.collection("stripeCustomers").doc(customerId);
}

function positiveMillis(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

async function ensureTrialState(uid, billing = {}) {
  let trialStartedAtMillis = positiveMillis(billing.trialStartedAtMillis);
  if (!trialStartedAtMillis) {
    const userRecord = await auth.getUser(uid);
    trialStartedAtMillis =
      positiveMillis(Date.parse(userRecord.metadata.creationTime))
      || Date.now();
    await billingRef(uid).set({
      trialStartedAtMillis,
      trialInitializedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }
  const serverNowMillis = Date.now();
  const trialEndsAtMillis = trialStartedAtMillis + TRIAL_DURATION_MS;
  return {
    billing: { ...billing, trialStartedAtMillis },
    serverNowEpochSeconds: Math.floor(serverNowMillis / 1000),
    trialActive: serverNowMillis < trialEndsAtMillis,
    trialEndsAtEpochSeconds: Math.floor(trialEndsAtMillis / 1000),
  };
}

async function acquireStatusRefreshLock(uid) {
  const ref = billingRef(uid);
  const now = Date.now();
  const lockToken = randomUUID();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const billing = snapshot.data() || {};
    const lastAttemptAt = positiveMillis(
      billing.statusRefreshLastAttemptAtMillis,
    );
    const lockExpiresAt = positiveMillis(
      billing.statusRefreshLockExpiresAtMillis,
    );
    const cooldownRetryAt = lastAttemptAt
      ? lastAttemptAt + STATUS_REFRESH_COOLDOWN_MS
      : 0;
    const activeLockRetryAt =
      billing.statusRefreshLockToken && lockExpiresAt > now
        ? lockExpiresAt
        : 0;
    const retryAt = Math.max(cooldownRetryAt, activeLockRetryAt);
    if (retryAt > now) {
      return {
        acquired: false,
        billing,
        retryAfterMs: retryAt - now,
      };
    }
    transaction.set(ref, {
      statusRefreshLockToken: lockToken,
      statusRefreshLockExpiresAtMillis: now + STATUS_REFRESH_LOCK_MS,
      statusRefreshLockAcquiredAt: FieldValue.serverTimestamp(),
      statusRefreshLastAttemptAtMillis: now,
    }, { merge: true });
    return { acquired: true, billing, lockToken };
  });
}

async function finishStatusRefreshLock(uid, lockToken) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("statusRefreshLockToken") !== lockToken) {
      throw new Error("Billing status refresh lock was lost");
    }
    transaction.set(ref, {
      lastStripeRefreshAtMillis: Date.now(),
      statusRefreshLockToken: FieldValue.delete(),
      statusRefreshLockExpiresAtMillis: FieldValue.delete(),
      statusRefreshLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

async function releaseStatusRefreshLock(uid, lockToken) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("statusRefreshLockToken") !== lockToken) return;
    transaction.set(ref, {
      statusRefreshLockToken: FieldValue.delete(),
      statusRefreshLockExpiresAtMillis: FieldValue.delete(),
      statusRefreshLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

async function acquirePortalLock(uid) {
  const ref = billingRef(uid);
  const now = Date.now();
  const lockToken = randomUUID();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const billing = snapshot.data() || {};
    const lastCreatedAt = positiveMillis(billing.portalLastCreatedAtMillis);
    const lastAttemptAt = positiveMillis(billing.portalLastAttemptAtMillis);
    const lockExpiresAt = positiveMillis(billing.portalLockExpiresAtMillis);
    const retryAt = Math.max(
      lastCreatedAt + PORTAL_COOLDOWN_MS,
      lastAttemptAt + PORTAL_COOLDOWN_MS,
      lockExpiresAt,
    );
    if (
      (lastCreatedAt && now < lastCreatedAt + PORTAL_COOLDOWN_MS)
      || (lastAttemptAt && now < lastAttemptAt + PORTAL_COOLDOWN_MS)
      || (billing.portalLockToken && lockExpiresAt > now)
    ) {
      return {
        acquired: false,
        billing,
        retryAfterMs: Math.max(1, retryAt - now),
      };
    }
    transaction.set(ref, {
      portalLockToken: lockToken,
      portalLockExpiresAtMillis: now + PORTAL_LOCK_MS,
      portalLockAcquiredAt: FieldValue.serverTimestamp(),
      portalLastAttemptAtMillis: now,
    }, { merge: true });
    return { acquired: true, billing, lockToken };
  });
}

async function finishPortalLock(uid, lockToken) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("portalLockToken") !== lockToken) {
      throw new Error("Customer portal lock was lost");
    }
    transaction.set(ref, {
      portalLastCreatedAtMillis: Date.now(),
      portalLockToken: FieldValue.delete(),
      portalLockExpiresAtMillis: FieldValue.delete(),
      portalLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

async function releasePortalLock(uid, lockToken) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("portalLockToken") !== lockToken) return;
    transaction.set(ref, {
      portalLockToken: FieldValue.delete(),
      portalLockExpiresAtMillis: FieldValue.delete(),
      portalLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

function channelRequestRateRef(uid) {
  return db.collection("users").doc(uid)
    .collection("serverState").doc("channelRequest");
}

async function acquireChannelRequestAttempt(uid) {
  const reference = channelRequestRateRef(uid);
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    const lastAttemptAt = positiveMillis(snapshot.get("lastAttemptAtMillis"));
    if (
      lastAttemptAt
      && now >= lastAttemptAt
      && now - lastAttemptAt < CHANNEL_REQUEST_COOLDOWN_MS
    ) {
      return {
        acquired: false,
        retryAfterMs:
          CHANNEL_REQUEST_COOLDOWN_MS - (now - lastAttemptAt),
      };
    }
    transaction.set(reference, {
      lastAttemptAtMillis: now,
      lastAttemptAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { acquired: true };
  });
}

async function acquireBugReportAttempt(uid) {
  const reference = db.collection("users").doc(uid)
    .collection("serverState").doc("bugReport");
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    const lastAttemptAt = positiveMillis(snapshot.get("lastAttemptAtMillis"));
    if (
      lastAttemptAt
      && now >= lastAttemptAt
      && now - lastAttemptAt < BUG_REPORT_COOLDOWN_MS
    ) {
      return {
        acquired: false,
        retryAfterMs: BUG_REPORT_COOLDOWN_MS - (now - lastAttemptAt),
      };
    }
    transaction.set(reference, {
      lastAttemptAtMillis: now,
      lastAttemptAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { acquired: true };
  });
}

function cleanSingleLine(value, maxLength) {
  return String(value || "")
    .replace(/[\u0000-\u001f\u007f]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, maxLength);
}

function normalizeYoutubeChannelUrl(value) {
  const raw = String(value || "").trim().slice(0, 2_048);
  if (/^@[A-Za-z0-9._-]{3,64}$/.test(raw)) {
    return `https://www.youtube.com/${raw}`;
  }
  if (/^UC[A-Za-z0-9_-]{22}$/.test(raw)) {
    return `https://www.youtube.com/channel/${raw}`;
  }
  try {
    const url = new URL(raw);
    const hostname = url.hostname.toLowerCase();
    return url.protocol === "https:"
      && (
        hostname === "youtube.com"
        || hostname.endsWith(".youtube.com")
        || hostname === "youtu.be"
      )
      ? url.toString()
      : null;
  } catch {
    return null;
  }
}

function newCheckoutIdempotencyKey(uid) {
  return `filtertube-checkout-${uid}-${randomUUID()}`;
}

async function acquireCheckoutLock(uid, plan) {
  const ref = billingRef(uid);
  const now = Date.now();
  const lockToken = randomUUID();
  const proposedIdempotencyKey = newCheckoutIdempotencyKey(uid);
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const billing = snapshot.data() || {};
    const lastAttemptAt = positiveMillis(billing.checkoutLastAttemptAtMillis);
    if (lastAttemptAt && now >= lastAttemptAt
      && now - lastAttemptAt < CHECKOUT_COOLDOWN_MS) {
      return {
        acquired: false,
        retryAfterMs: CHECKOUT_COOLDOWN_MS - (now - lastAttemptAt),
      };
    }
    const lockExpiresAtMillis = Number(billing.checkoutLockExpiresAtMillis || 0);
    if (billing.checkoutLockToken && lockExpiresAtMillis > now) {
      return {
        acquired: false,
        retryAfterMs: lockExpiresAtMillis - now,
      };
    }

    const reusedIdempotencyKey = billing.checkoutIdempotencyPlan === plan
      && typeof billing.checkoutIdempotencyKey === "string"
      && billing.checkoutIdempotencyKey.length > 0;
    const idempotencyKey = reusedIdempotencyKey
      ? billing.checkoutIdempotencyKey
      : proposedIdempotencyKey;
    transaction.set(ref, {
      checkoutLockToken: lockToken,
      checkoutLockExpiresAtMillis: now + CHECKOUT_LOCK_MS,
      checkoutLockAcquiredAt: FieldValue.serverTimestamp(),
      checkoutLastAttemptAtMillis: now,
      checkoutIdempotencyKey: idempotencyKey,
      checkoutIdempotencyPlan: plan,
    }, { merge: true });
    return {
      acquired: true,
      billing,
      idempotencyKey,
      lockToken,
      reusedIdempotencyKey,
    };
  });
}

async function rotateCheckoutIdempotencyKey(uid, lockToken, plan) {
  const ref = billingRef(uid);
  const idempotencyKey = newCheckoutIdempotencyKey(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("checkoutLockToken") !== lockToken) {
      throw new Error("Checkout lock was lost");
    }
    transaction.set(ref, {
      checkoutIdempotencyKey: idempotencyKey,
      checkoutIdempotencyPlan: plan,
    }, { merge: true });
  });
  return idempotencyKey;
}

async function finishCheckoutLock(uid, lockToken, session, plan, idempotencyKey) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("checkoutLockToken") !== lockToken) {
      throw new Error("Checkout lock was lost before the session was saved");
    }
    transaction.set(ref, {
      checkoutSessionId: session.id,
      checkoutPlan: plan,
      checkoutSessionIdempotencyKey: idempotencyKey,
      checkoutCreatedAt: FieldValue.serverTimestamp(),
      checkoutLockToken: FieldValue.delete(),
      checkoutLockExpiresAtMillis: FieldValue.delete(),
      checkoutLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

async function releaseCheckoutLock(uid, lockToken) {
  const ref = billingRef(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("checkoutLockToken") !== lockToken) return;
    transaction.set(ref, {
      checkoutLockToken: FieldValue.delete(),
      checkoutLockExpiresAtMillis: FieldValue.delete(),
      checkoutLockAcquiredAt: FieldValue.delete(),
    }, { merge: true });
  });
}

function isStripeResourceMissing(error) {
  return error?.code === "resource_missing"
    || error?.raw?.code === "resource_missing";
}

async function retrieveCheckoutSession(stripe, sessionId) {
  if (!sessionId) return null;
  try {
    return await stripe.checkout.sessions.retrieve(sessionId);
  } catch (error) {
    if (isStripeResourceMissing(error)) return null;
    throw error;
  }
}

async function retrieveSubscription(stripe, subscriptionId, resourceMissingFallback = null) {
  if (!subscriptionId) return null;
  try {
    return await stripe.subscriptions.retrieve(subscriptionId, {
      expand: ["latest_invoice"],
    });
  } catch (error) {
    if (isStripeResourceMissing(error)) return resourceMissingFallback;
    throw error;
  }
}

async function listCustomerSubscriptions(stripe, customerId) {
  if (!customerId) return { customerMissing: false, subscriptions: [] };
  try {
    const result = await stripe.subscriptions.list({
      customer: customerId,
      status: "all",
      limit: 100,
      expand: ["data.latest_invoice"],
    });
    return {
      customerMissing: false,
      subscriptions: result.data,
    };
  } catch (error) {
    if (isStripeResourceMissing(error)) {
      return { customerMissing: true, subscriptions: [] };
    }
    throw error;
  }
}

/**
 * Saves Stripe-derived state with compare-and-swap semantics. A Stripe API
 * response can become stale while it is in flight, so every successful write
 * increments stripeStateRevision. Callers must refetch Stripe after conflicts.
 */
async function saveBilling(uid, data, event, expectedRevision) {
  const ref = billingRef(uid);
  const customerId = stripeId(data.stripeCustomerId);
  const payload = {
    ...data,
    updatedAt: FieldValue.serverTimestamp(),
  };
  const customerOwner = customerId
    ? {
      uid,
      updatedAt: FieldValue.serverTimestamp(),
    }
    : null;
  return db.runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    const existingData = existing.data() || {};
    const currentRevision = Number(
      existingData.stripeStateRevision || 0,
    );
    if (currentRevision !== expectedRevision) {
      return {
        billing: existingData,
        outcome: "conflict",
      };
    }
    const backfillCustomerOwner = () => {
      if (customerOwner) {
        transaction.set(
          stripeCustomerRef(customerId),
          customerOwner,
          { merge: true },
        );
      }
    };
    if (event && existingData.lastStripeEventId === event.id) {
      backfillCustomerOwner();
      return {
        billing: existingData,
        outcome: "duplicate",
      };
    }
    const previousEventCreated = Number(
      existingData.lastStripeEventCreated || 0,
    );
    if (event && previousEventCreated > event.created) {
      backfillCustomerOwner();
      return {
        billing: existingData,
        outcome: "stale",
      };
    }
    const nextRevision = currentRevision + 1;
    const stripeState = {
      ...payload,
      stripeStateRevision: nextRevision,
    };
    if (event) {
      stripeState.lastStripeEventId = event.id;
      stripeState.lastStripeEventCreated = event.created;
    }
    transaction.set(ref, stripeState, { merge: true });
    backfillCustomerOwner();
    return {
      billing: {
        ...existingData,
        ...data,
        stripeStateRevision: nextRevision,
        ...(event
          ? {
            lastStripeEventId: event.id,
            lastStripeEventCreated: event.created,
          }
          : {}),
      },
      outcome: "saved",
    };
  });
}

function publicBillingStatus(data = {}, trial = {}) {
  const manualPremiumActive = Boolean(data.manualPremiumActive);
  return {
    active: manualPremiumActive || Boolean(data.active),
    status: manualPremiumActive ? "complimentary" : (data.status || "inactive"),
    plan: data.plan || null,
    currentPeriodEnd: data.currentPeriodEnd || null,
    cancelAtPeriodEnd: Boolean(data.cancelAtPeriodEnd),
    lastInvoiceStatus: data.lastInvoiceStatus || null,
    canManage: Boolean(data.stripeCustomerId),
    trialActive: Boolean(trial.trialActive),
    trialEndsAt: trial.trialEndsAtEpochSeconds || null,
    serverNow: trial.serverNowEpochSeconds || null,
  };
}

function subscriptionBlocksCheckout(subscription) {
  return Boolean(managedSubscriptionPlan(subscription))
    && !["canceled", "incomplete_expired"].includes(subscription?.status);
}

function canonicalSubscriptions(subscriptions, preferredSubscriptionId = null) {
  const byId = new Map();
  for (const subscription of subscriptions) {
    const id = stripeId(subscription);
    if (id && managedSubscriptionPlan(subscription)) {
      byId.set(id, subscription);
    }
  }
  const all = [...byId.values()];
  const active = all.filter(
    (subscription) => subscriptionEntitled(subscription),
  );
  active.sort((left, right) => {
    const preferredDifference =
      Number(stripeId(right) === preferredSubscriptionId)
      - Number(stripeId(left) === preferredSubscriptionId);
    if (preferredDifference) return preferredDifference;
    const statusDifference =
      Number(right.status === "active") - Number(left.status === "active");
    if (statusDifference) return statusDifference;
    return Number(left.created || 0) - Number(right.created || 0);
  });

  const statusPriority = new Map([
    ["past_due", 6],
    ["incomplete", 5],
    ["unpaid", 4],
    ["paused", 3],
    ["canceled", 2],
    ["incomplete_expired", 1],
  ]);
  const byRelevance = (left, right) => {
    const priorityDifference =
      (statusPriority.get(right.status) || 0)
      - (statusPriority.get(left.status) || 0);
    if (priorityDifference) return priorityDifference;
    return Number(right.created || 0) - Number(left.created || 0);
  };
  const blocking = all.filter(subscriptionBlocksCheckout).sort(byRelevance);
  let canonical = active[0] || null;
  const preferredSubscription = preferredSubscriptionId
    ? all.find(
      (subscription) => stripeId(subscription) === preferredSubscriptionId,
    ) || null
    : null;
  if (
    !canonical
    && preferredSubscription
    && subscriptionBlocksCheckout(preferredSubscription)
  ) {
    canonical = preferredSubscription;
  }
  if (!canonical) canonical = blocking[0] || null;
  if (!canonical) canonical = preferredSubscription;
  if (!canonical) canonical = [...all].sort(byRelevance)[0] || null;
  return { active, all, blocking, canonical };
}

async function reconcileBillingWithStripe(
  stripe,
  uid,
  initialBilling = {},
  options = {},
) {
  let billing = initialBilling;
  for (
    let attempt = 0;
    attempt < BILLING_RECONCILE_MAX_ATTEMPTS;
    attempt += 1
  ) {
    const preferredCanonicalId = stripeId(
      billing.stripeSubscriptionId,
    );
    const lookupSubscriptionId = stripeId(
      options.subscriptionId || preferredCanonicalId,
    );
    let preferredSubscription =
      attempt === 0 ? options.preferredSubscription || null : null;
    const authoritativeReference = Boolean(
      options.customerId
        || billing.stripeCustomerId
        || lookupSubscriptionId
        || preferredSubscription,
    );
    if (!authoritativeReference) {
      return {
        activeSubscriptions: [],
        authoritative: false,
        billing,
        blockingSubscriptions: [],
        canonicalSubscription: null,
        subscriptions: [],
      };
    }

    if (!preferredSubscription && lookupSubscriptionId) {
      const deletionFallback =
        options.event?.type === "customer.subscription.deleted"
          ? options.preferredSubscription || null
          : null;
      preferredSubscription = await retrieveSubscription(
        stripe,
        lookupSubscriptionId,
        deletionFallback,
      );
    }
    const customerId = stripeId(
      options.customerId
        || preferredSubscription?.customer
        || billing.stripeCustomerId,
    );
    const listed = await listCustomerSubscriptions(stripe, customerId);
    const combinedSubscriptions = [...listed.subscriptions];
    if (preferredSubscription) {
      combinedSubscriptions.push(preferredSubscription);
    }
    const selected = canonicalSubscriptions(
      combinedSubscriptions,
      preferredCanonicalId,
    );
    const resolvedCustomerId = listed.customerMissing ? null : customerId;
    const data = selected.canonical
      ? {
        ...billingFromSubscription(selected.canonical),
        stripeCustomerId: resolvedCustomerId,
        duplicateActiveSubscriptionIds: selected.active
          .filter(
            (subscription) =>
              stripeId(subscription) !== stripeId(selected.canonical),
          )
          .map(stripeId),
      }
      : {
        active: false,
        status: "inactive",
        stripeCustomerId: resolvedCustomerId,
        stripeSubscriptionId: null,
        plan: null,
        cancelAtPeriodEnd: false,
        currentPeriodEnd: null,
        duplicateActiveSubscriptionIds: [],
      };
    if (options.lastInvoiceStatus) {
      data.lastInvoiceStatus = options.lastInvoiceStatus;
    }
    if (selected.active.length > 1) {
      console.error("Multiple active Stripe subscriptions detected", {
        uid,
        subscriptionIds: selected.active.map(stripeId),
        canonicalSubscriptionId: stripeId(selected.canonical),
      });
    }

    const expectedRevision = Number(
      billing.stripeStateRevision || 0,
    );
    const saved = await saveBilling(
      uid,
      data,
      options.event || null,
      expectedRevision,
    );
    if (saved.outcome === "conflict") {
      billing = saved.billing;
      continue;
    }
    return {
      activeSubscriptions: selected.active,
      authoritative: true,
      billing: saved.billing,
      blockingSubscriptions: selected.blocking,
      canonicalSubscription: selected.canonical,
      subscriptions: selected.all,
    };
  }
  throw new Error("Billing state changed repeatedly during reconciliation");
}

function subscriptionConflict(res, reconciliation, fallbackBilling = {}) {
  const hasActiveSubscription =
    reconciliation.activeSubscriptions.length > 0
    || managedBillingEntitled(fallbackBilling);
  return res.status(409).json({
    ok: false,
    code: hasActiveSubscription
      ? "ALREADY_ACTIVE"
      : "SUBSCRIPTION_REQUIRES_ATTENTION",
    message: hasActiveSubscription
      ? "An active Premium subscription already exists"
      : "An existing subscription must be managed before starting a new one",
  });
}

export const submitChannelRequest = onRequest({
  ...PAYMENT_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") {
    return res.status(405).json({ ok: false, message: "POST required" });
  }
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;

  const name = cleanSingleLine(req.body?.name, 120);
  const channelUrl = normalizeYoutubeChannelUrl(req.body?.url);
  const category = cleanSingleLine(req.body?.category, 40);
  const gender = cleanSingleLine(req.body?.gender, 16) || "all";
  const description = cleanSingleLine(req.body?.description, 1_000);
  if (
    !name
    || !channelUrl
    || !CHANNEL_REQUEST_CATEGORIES.has(category)
    || !["all", "male", "female"].includes(gender)
  ) {
    return res.status(400).json({
      ok: false,
      message: "Invalid channel request",
    });
  }

  try {
    const rateLimit = await acquireChannelRequestAttempt(decoded.uid);
    if (!rateLimit.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(rateLimit.retryAfterMs / 1000))),
      );
      return res.status(429).json({
        ok: false,
        code: "CHANNEL_REQUEST_RATE_LIMITED",
        message: "Please wait before sending another channel request",
      });
    }

    const requestVersion = randomUUID();
    await db.collection("channelRequests").doc(decoded.uid).set({
      ownerUid: decoded.uid,
      requestVersion,
      name,
      url: channelUrl,
      category,
      gender,
      description,
      status: "pending",
      submittedAt: FieldValue.serverTimestamp(),
    });
    return res.json({ ok: true });
  } catch (error) {
    console.error("submitChannelRequest failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to save the channel request",
    });
  }
});

export const listChannelRequests = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "GET") {
    return res.status(405).json({ ok: false, message: "GET required" });
  }
  const decoded = await requireUser(req, res, true);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  if (!requireAdmin(decoded, res)) return;

  try {
    res.set("Cache-Control", "private, no-store");
    const snapshot = await db.collection("channelRequests")
      .where("status", "==", "pending")
      .orderBy("submittedAt", "desc")
      .limit(100)
      .get();
    const requests = snapshot.docs.map((document) => {
      const data = document.data();
      const submittedAt = data.submittedAt?.toDate?.();
      return {
        id: document.id,
        version: cleanSingleLine(data.requestVersion, 64),
        name: cleanSingleLine(data.name, 120),
        url: normalizeYoutubeChannelUrl(data.url) || "",
        category: cleanSingleLine(data.category, 40),
        gender: cleanSingleLine(data.gender, 16),
        description: cleanSingleLine(data.description, 1_000),
        requestedAt: submittedAt instanceof Date
          ? submittedAt.toISOString()
          : "",
      };
    });
    return res.json({ ok: true, requests });
  } catch (error) {
    console.error("listChannelRequests failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to load channel requests",
    });
  }
});

export const resolveChannelRequest = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") {
    return res.status(405).json({ ok: false, message: "POST required" });
  }
  const decoded = await requireUser(req, res, true);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  if (!requireAdmin(decoded, res)) return;

  const requestId = cleanSingleLine(req.body?.id, 128);
  const requestVersion = cleanSingleLine(req.body?.version, 64);
  const resolution = cleanSingleLine(req.body?.resolution, 16);
  if (
    !requestId
    || !/^[A-Za-z0-9_-]{1,128}$/.test(requestId)
    || !/^[0-9a-f-]{36}$/i.test(requestVersion)
    || !["approved", "rejected"].includes(resolution)
  ) {
    return res.status(400).json({
      ok: false,
      message: "Invalid channel request resolution",
    });
  }

  try {
    const requestRef = db.collection("channelRequests").doc(requestId);
    const result = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(requestRef);
      if (!snapshot.exists) {
        return {
          actualStatus: null,
          alreadyResolved: false,
          found: false,
          versionMatches: false,
        };
      }
      if (snapshot.get("requestVersion") !== requestVersion) {
        return {
          actualStatus: snapshot.get("status") || null,
          alreadyResolved: false,
          found: true,
          versionMatches: false,
        };
      }
      const actualStatus = snapshot.get("status") || "pending";
      if (actualStatus !== "pending") {
        return {
          actualStatus,
          alreadyResolved: actualStatus === resolution,
          found: true,
          versionMatches: true,
        };
      }
      transaction.update(requestRef, {
        status: resolution,
        resolvedAt: FieldValue.serverTimestamp(),
        resolvedByUid: decoded.uid,
      });
      return {
        actualStatus: resolution,
        alreadyResolved: false,
        found: true,
        versionMatches: true,
      };
    });
    if (!result.found) {
      return res.status(404).json({
        ok: false,
        message: "Channel request was not found",
      });
    }
    if (!result.versionMatches) {
      return res.status(409).json({
        ok: false,
        code: "CHANNEL_REQUEST_CHANGED",
        message: "Channel request changed; reload it before resolving",
      });
    }
    if (
      result.actualStatus !== resolution
      && result.actualStatus !== "pending"
    ) {
      return res.status(409).json({
        ok: false,
        code: "CHANNEL_REQUEST_ALREADY_RESOLVED",
        actualStatus: result.actualStatus,
        message: "Channel request was already resolved differently",
      });
    }
    console.info("Channel request resolved", {
      adminUid: decoded.uid,
      requestId,
      resolution,
    });
    return res.json({ ok: true, alreadyResolved: result.alreadyResolved });
  } catch (error) {
    console.error("resolveChannelRequest failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to resolve the channel request",
    });
  }
});

export const submitBugReport = onRequest({
  ...PAYMENT_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") {
    return res.status(405).json({ ok: false, message: "POST required" });
  }
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;

  const report = String(req.body?.report || "")
    .replace(/\u0000/g, "")
    .trim()
    .slice(0, 50_000);
  const note = cleanSingleLine(req.body?.note, 1_000);
  if (!report) {
    return res.status(400).json({
      ok: false,
      message: "A bug report is required",
    });
  }

  try {
    const rateLimit = await acquireBugReportAttempt(decoded.uid);
    if (!rateLimit.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(rateLimit.retryAfterMs / 1000))),
      );
      return res.status(429).json({
        ok: false,
        code: "BUG_REPORT_RATE_LIMITED",
        message: "Please wait before sending another bug report",
      });
    }
    await db.collection("bugReports").doc(decoded.uid).set({
      ownerUid: decoded.uid,
      report,
      note,
      status: "pending",
      submittedAt: FieldValue.serverTimestamp(),
    });
    return res.json({ ok: true });
  } catch (error) {
    console.error("submitBugReport failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to save the bug report",
    });
  }
});

export const createCheckout = onRequest({
  ...PAYMENT_HTTP_OPTIONS,
  secrets: [stripeSecret],
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;

  const plan = req.body?.plan;
  const price = priceFor(plan);
  if (!price) return res.status(400).json({ ok: false, message: "Unknown plan" });

  let checkoutLock = null;
  try {
    checkoutLock = await acquireCheckoutLock(decoded.uid, plan);
    if (!checkoutLock.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(checkoutLock.retryAfterMs / 1000))),
      );
      return res.status(409).json({
        ok: false,
        code: "CHECKOUT_IN_PROGRESS",
        message: "A checkout request is already being prepared",
      });
    }

    const stripe = stripeClient();
    let billing = checkoutLock.billing;
    let reconciliation = await reconcileBillingWithStripe(
      stripe,
      decoded.uid,
      billing,
    );
    billing = reconciliation.billing;
    if (
      reconciliation.blockingSubscriptions.length > 0
      || managedBillingEntitled(billing)
    ) {
      return subscriptionConflict(res, reconciliation, billing);
    }

    let customerId = stripeId(billing.stripeCustomerId);
    let idempotencyKey = checkoutLock.idempotencyKey;
    const previousCheckoutId = stripeId(billing.checkoutSessionId);
    if (previousCheckoutId) {
      const previousCheckout = await retrieveCheckoutSession(
        stripe,
        previousCheckoutId,
      );
      const previousCustomerId = stripeId(previousCheckout?.customer);
      if (previousCustomerId) customerId = previousCustomerId;

      if (
        previousCheckout?.status === "complete"
        && (previousCustomerId || stripeId(previousCheckout.subscription))
      ) {
        const completedSubscriptionId = stripeId(
          previousCheckout.subscription,
        );
        const completedSubscription = await retrieveSubscription(
          stripe,
          completedSubscriptionId,
          null,
        );
        reconciliation = await reconcileBillingWithStripe(
          stripe,
          decoded.uid,
          billing,
          {
            customerId: previousCustomerId,
            preferredSubscription: completedSubscription,
            subscriptionId: completedSubscriptionId,
          },
        );
        billing = reconciliation.billing;
        customerId = stripeId(billing.stripeCustomerId) || customerId;
        if (reconciliation.blockingSubscriptions.length > 0) {
          return subscriptionConflict(res, reconciliation, billing);
        }
      }

      const previousPlan = normalizePlan(previousCheckout?.metadata?.plan)
        || normalizePlan(billing.checkoutPlan);
      if (previousCheckout?.status === "open") {
        if (previousPlan === plan && previousCheckout.url) {
          return res.json({
            ok: true,
            checkoutUrl: previousCheckout.url,
          });
        }
        await stripe.checkout.sessions.expire(previousCheckout.id);
      }

      const idempotencyCreatedPreviousSession =
        billing.checkoutSessionIdempotencyKey === idempotencyKey;
      if (
        checkoutLock.reusedIdempotencyKey
        && idempotencyCreatedPreviousSession
        && (
          !previousCheckout
          || previousCheckout.status !== "open"
          || previousPlan !== plan
          || !previousCheckout.url
        )
      ) {
        idempotencyKey = await rotateCheckoutIdempotencyKey(
          decoded.uid,
          checkoutLock.lockToken,
          plan,
        );
      }
    }

    const sessionOptions = {
      mode: "subscription",
      payment_method_types: ["card"],
      line_items: [{ price, quantity: 1 }],
      client_reference_id: decoded.uid,
      metadata: { firebaseUid: decoded.uid, plan },
      subscription_data: {
        metadata: { firebaseUid: decoded.uid, plan },
      },
      success_url: successUrl.value(),
      cancel_url: cancelUrl.value(),
      allow_promotion_codes: true,
    };
    if (customerId) sessionOptions.customer = customerId;
    else if (decoded.email) sessionOptions.customer_email = decoded.email;

    const session = await stripe.checkout.sessions.create(
      sessionOptions,
      { idempotencyKey },
    );
    if (!session.url) throw new Error("Stripe Checkout did not return a URL");
    await finishCheckoutLock(
      decoded.uid,
      checkoutLock.lockToken,
      session,
      plan,
      idempotencyKey,
    );
    return res.json({ ok: true, checkoutUrl: session.url });
  } catch (error) {
    console.error("createCheckout failed", error);
    return res.status(502).json({ ok: false, message: "Unable to start checkout" });
  } finally {
    if (checkoutLock?.acquired) {
      await releaseCheckoutLock(
        decoded.uid,
        checkoutLock.lockToken,
      ).catch((error) => {
        console.error("Could not release checkout lock", error);
      });
    }
  }
});

export const createCustomerPortal = onRequest({
  ...PAYMENT_HTTP_OPTIONS,
  secrets: [stripeSecret],
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;

  let portalLock = null;
  try {
    portalLock = await acquirePortalLock(decoded.uid);
    if (!portalLock.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(portalLock.retryAfterMs / 1000))),
      );
      return res.status(429).json({
        ok: false,
        code: "PORTAL_RATE_LIMITED",
        message: "Please wait before opening subscription management again",
      });
    }
    const billing = portalLock.billing;
    const customerId = stripeId(billing.stripeCustomerId);
    if (!customerId) return res.status(404).json({ ok: false, message: "No Stripe customer was found" });
    const session = await stripeClient().billingPortal.sessions.create({
      customer: customerId,
      return_url: portalReturnUrl.value(),
    });
    await finishPortalLock(
      decoded.uid,
      portalLock.lockToken,
    );
    return res.json({ ok: true, portalUrl: session.url });
  } catch (error) {
    console.error("createCustomerPortal failed", error);
    return res.status(502).json({ ok: false, message: "Unable to open subscription management" });
  } finally {
    if (portalLock?.acquired) {
      await releasePortalLock(
        decoded.uid,
        portalLock.lockToken,
      ).catch((error) => {
        console.error("Could not release customer portal lock", error);
      });
    }
  }
});

/**
 * Fast entitlement snapshot that does not call Stripe.
 *
 * A new account's 30-day trial must not depend on Stripe availability or
 * configuration. Paid fields come only from the server-owned billing document;
 * billingStatus remains the authoritative Stripe reconciliation endpoint.
 */
export const trialStatus = onRequest({
  ...STATUS_HTTP_OPTIONS,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "GET") {
    return res.status(405).json({ ok: false, message: "GET required" });
  }
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  res.set("Cache-Control", "private, no-store");
  try {
    const snapshot = await billingRef(decoded.uid).get();
    const trial = await ensureTrialState(
      decoded.uid,
      snapshot.data() || {},
    );
    return res.json({
      ok: true,
      billing: publicBillingStatus(trial.billing, trial),
    });
  } catch (error) {
    console.error("trialStatus failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to initialize trial entitlement",
    });
  }
});

export const billingStatus = onRequest({
  ...STATUS_HTTP_OPTIONS,
  secrets: [stripeSecret],
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "GET") return res.status(405).json({ ok: false, message: "GET required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  res.set("Cache-Control", "private, no-store");
  let statusLock = null;
  try {
    statusLock = await acquireStatusRefreshLock(decoded.uid);
    if (!statusLock.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(statusLock.retryAfterMs / 1000))),
      );
      return res.status(429).json({
        ok: false,
        code: "BILLING_REFRESH_RATE_LIMITED",
        message: "Please wait before refreshing subscription status again",
      });
    }
    const trial = await ensureTrialState(
      decoded.uid,
      statusLock.billing,
    );
    const billing = trial.billing;
    let reconciliation = await reconcileBillingWithStripe(
      stripeClient(),
      decoded.uid,
      billing,
    );
    const checkoutSessionId = stripeId(
      reconciliation.billing.checkoutSessionId,
    );
    const needsCheckoutRecovery =
      !reconciliation.authoritative
      || !stripeId(reconciliation.billing.stripeCustomerId)
      || !stripeId(reconciliation.billing.stripeSubscriptionId);
    if (checkoutSessionId && needsCheckoutRecovery) {
      const stripe = stripeClient();
      const checkoutSession = await retrieveCheckoutSession(
        stripe,
        checkoutSessionId,
      );
      if (checkoutSession?.status === "complete") {
        const subscriptionId = stripeId(checkoutSession.subscription);
        const subscription = await retrieveSubscription(
          stripe,
          subscriptionId,
          null,
        );
        reconciliation = await reconcileBillingWithStripe(
          stripe,
          decoded.uid,
          reconciliation.billing,
          {
            customerId:
              stripeId(checkoutSession.customer)
              || stripeId(subscription?.customer),
            preferredSubscription: subscription,
            subscriptionId,
          },
        );
      }
    }
    await finishStatusRefreshLock(
      decoded.uid,
      statusLock.lockToken,
    );
    return res.json({
      ok: true,
      billing: publicBillingStatus(reconciliation.billing, trial),
    });
  } catch (error) {
    console.error("billingStatus reconciliation failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to refresh subscription status",
    });
  } finally {
    if (statusLock?.acquired) {
      await releaseStatusRefreshLock(
        decoded.uid,
        statusLock.lockToken,
      ).catch((error) => {
        console.error("Could not release billing status lock", error);
      });
    }
  }
});

async function uidFromSubscription(subscription) {
  const metadataUid = subscription?.metadata?.firebaseUid;
  if (metadataUid) return metadataUid;
  const customerId = stripeId(subscription?.customer);
  return uidFromCustomer(customerId);
}

async function uidFromCustomer(customerId) {
  if (!customerId) return null;
  const directReference = stripeCustomerRef(customerId);
  const direct = await directReference.get();
  const directUid = direct.get("uid");
  if (typeof directUid === "string" && directUid.length > 0) {
    return directUid;
  }

  const snapshot = await db.collectionGroup("billing")
    .where("stripeCustomerId", "==", customerId)
    .limit(1)
    .get();
  const uid = snapshot.empty
    ? null
    : snapshot.docs[0].ref.parent.parent?.id || null;
  if (uid) {
    await directReference.set({
      uid,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }
  return uid;
}

function billingFromSubscription(subscription) {
  const managedPlan = managedSubscriptionPlan(subscription);
  const latestInvoice = expandedLatestInvoice(subscription);
  return {
    active: Boolean(managedPlan) && subscriptionEntitled(subscription),
    status: subscription?.status || "inactive",
    stripeCustomerId: stripeId(subscription?.customer),
    stripeSubscriptionId: stripeId(subscription),
    // Entitlement must come from an actual configured Stripe Price. Metadata
    // is useful for diagnostics, but can never turn another product into
    // FilterTube Premium.
    plan: managedPlan,
    cancelAtPeriodEnd: Boolean(subscription?.cancel_at_period_end),
    currentPeriodEnd: subscriptionPeriodEnd(subscription),
    lastInvoiceStatus:
      latestInvoice?.status
      || (latestInvoice?.paid === true ? "paid" : null),
  };
}

export const stripeWebhook = onRequest({
  ...WEBHOOK_HTTP_OPTIONS,
  secrets: [stripeSecret, webhookSecret],
}, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("POST required");

  let event;
  let stripe;
  try {
    stripe = stripeClient();
    const payload = req.rawBody || Buffer.from(JSON.stringify(req.body || {}));
    event = stripe.webhooks.constructEvent(payload, req.get("stripe-signature"), webhookSecret.value());
    if (event.api_version !== STRIPE_API_VERSION) {
      console.warn("Stripe webhook API version differs from the server version", {
        eventId: event.id,
        eventType: event.type,
        expectedApiVersion: STRIPE_API_VERSION,
        receivedApiVersion: event.api_version || null,
      });
    }
  } catch (error) {
    console.error("stripeWebhook signature failure", error);
    return res.status(400).send("Invalid webhook signature");
  }

  try {
    const object = event.data.object;
    if (event.type === "checkout.session.completed") {
      const uid = object.metadata?.firebaseUid || object.client_reference_id;
      const subscriptionId = stripeId(object.subscription);
      const subscription = await retrieveSubscription(
        stripe,
        subscriptionId,
        null,
      );
      if (uid) {
        const billing = (await billingRef(uid).get()).data() || {};
        await reconcileBillingWithStripe(stripe, uid, billing, {
          customerId:
            stripeId(object.customer) || stripeId(subscription?.customer),
          event,
          preferredSubscription: subscription,
          subscriptionId,
        });
      }
    } else if (event.type.startsWith("customer.subscription.")) {
      // Fetch the current object because Stripe can deliver events out of order.
      // A stale created/updated event must never become an entitlement after
      // Stripe says the subscription no longer exists. Only a deletion event
      // may use its signed payload as an identity fallback, forced inactive.
      const deletionFallback =
        event.type === "customer.subscription.deleted"
          ? { ...object, status: "canceled" }
          : null;
      const subscription = await retrieveSubscription(
        stripe,
        object.id,
        deletionFallback,
      );
      const uid = await uidFromSubscription(subscription || object);
      if (uid) {
        const billing = (await billingRef(uid).get()).data() || {};
        await reconcileBillingWithStripe(stripe, uid, billing, {
          customerId:
            stripeId(subscription?.customer) || stripeId(object.customer),
          event,
          preferredSubscription: subscription,
          subscriptionId: stripeId(subscription),
        });
      }
    } else if ([
      "invoice.paid",
      "invoice.payment_failed",
      "invoice.finalization_failed",
      "invoice.payment_action_required",
    ].includes(event.type)) {
      const subscriptionId = invoiceSubscriptionId(object);
      const subscription = await retrieveSubscription(
        stripe,
        subscriptionId,
        null,
      );
      const customerId =
        stripeId(object.customer) || stripeId(subscription?.customer);
      const uid =
        await uidFromSubscription(subscription)
        || await uidFromCustomer(customerId);
      if (uid) {
        const billing = (await billingRef(uid).get()).data() || {};
        await reconcileBillingWithStripe(stripe, uid, billing, {
          customerId,
          event,
          lastInvoiceStatus: {
            "invoice.paid": "paid",
            "invoice.payment_failed": "payment_failed",
            "invoice.finalization_failed": "finalization_failed",
            "invoice.payment_action_required": "payment_action_required",
          }[event.type],
          preferredSubscription: subscription,
          subscriptionId,
        });
      }
    }
    return res.json({ received: true });
  } catch (error) {
    console.error("stripeWebhook processing failure", error);
    return res.status(500).send("Webhook processing failed");
  }
});
