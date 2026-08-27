import { onRequest } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { createHash, randomUUID } from "node:crypto";
import { getMessaging } from "firebase-admin/messaging";
import {
  createCreemCheckout,
  createCreemCustomerPortal,
  normalizeCreemEvent,
  verifyCreemWebhook,
} from "./billing/creem.js";

if (getApps().length === 0) initializeApp();

const auth = getAuth();
const db = getFirestore();
const creemApiKey = defineSecret("CREEM_API_KEY");
const creemWebhookSecret = defineSecret("CREEM_WEBHOOK_SECRET");
const creemMonthlyProduct = defineString("CREEM_MONTHLY_PRODUCT_ID");
const creemYearlyProduct = defineString("CREEM_YEARLY_PRODUCT_ID");
const creemApiBase = defineString("CREEM_API_BASE", {
  default: "https://api.creem.io",
});
const creemSuccessUrl = defineString("CREEM_SUCCESS_URL", {
  default: "https://filter-tube-52d8e.web.app/premium/success",
});

const REGION = "europe-west1";
const ADMIN_EMAIL = "ywldyld@gmail.com";
const CHECKOUT_LOCK_MS = 90_000;
const CHECKOUT_COOLDOWN_MS = 15_000;
const STATUS_REFRESH_COOLDOWN_MS = 15_000;
const STATUS_REFRESH_LOCK_MS = 30_000;
const PORTAL_COOLDOWN_MS = 15_000;
const PORTAL_LOCK_MS = 30_000;
const CHANNEL_REQUEST_COOLDOWN_MS = 30 * 1000;
const PREMIUM_REQUEST_COOLDOWN_MS = 10 * 60 * 1000;
const BUG_REPORT_COOLDOWN_MS = 10 * 60 * 1000;
const TRIAL_DURATION_MS = 30 * 24 * 60 * 60 * 1000;
const MANUAL_PLAN_DETAILS = Object.freeze({
  month: Object.freeze({ priceUsd: "$3.27", label: "חודשי", period: "לחודש" }),
  year: Object.freeze({ priceUsd: "$22.89", label: "שנתי", period: "לשנה" }),
});
const CHANNELS_SOURCE_URL =
  "https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json";
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
  cpu: 1,
  maxInstances: 10,
  concurrency: 20,
  timeoutSeconds: 30,
};
const STATUS_HTTP_OPTIONS = {
  region: REGION,
  cpu: 1,
  maxInstances: 10,
  concurrency: 40,
  timeoutSeconds: 30,
};
const WEBHOOK_HTTP_OPTIONS = {
  region: REGION,
  cpu: 1,
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
  const email = cleanSingleLine(decoded.email, 254).toLowerCase();
  if (decoded.admin === true || email === ADMIN_EMAIL) return true;
  res.status(403).json({
    ok: false,
    code: "ADMIN_REQUIRED",
    message: "Administrator access is required",
  });
  return false;
}

function normalizePlan(plan) {
  return plan === "month" || plan === "year" ? plan : null;
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
      lastBillingRefreshAtMillis: Date.now(),
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

async function acquirePremiumRequestAttempt(uid) {
  const reference = db.collection("users").doc(uid)
    .collection("serverState").doc("premiumRequest");
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    const lastAttemptAt = positiveMillis(snapshot.get("lastAttemptAtMillis"));
    if (
      lastAttemptAt
      && now >= lastAttemptAt
      && now - lastAttemptAt < PREMIUM_REQUEST_COOLDOWN_MS
    ) {
      return {
        acquired: false,
        retryAfterMs: PREMIUM_REQUEST_COOLDOWN_MS - (now - lastAttemptAt),
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

async function ensureApprovedChannelsSeeded() {
  const collection = db.collection("approvedChannels");
  const current = await collection.limit(1).get();
  if (!current.empty) return;
  const response = await fetch(`${CHANNELS_SOURCE_URL}?seed=${Date.now()}`);
  if (!response.ok) throw new Error(`Unable to seed channels (${response.status})`);
  const source = await response.json();
  if (!Array.isArray(source) || source.length === 0) return;
  const batch = db.batch();
  for (const item of source.slice(0, 500)) {
    const id = cleanSingleLine(item?.youtubeChannelId || item?.youtube_channel_id, 100);
    const name = cleanSingleLine(item?.name, 200);
    const category = cleanSingleLine(item?.category, 40);
    const gender = cleanSingleLine(item?.gender, 16) || "all";
    if (!/^UC[A-Za-z0-9_-]{20,}$/.test(id) || !name
      || !CHANNEL_REQUEST_CATEGORIES.has(category)
      || !["all", "male", "female"].includes(gender)) continue;
    batch.set(collection.doc(id), {
      youtubeChannelId: id, name, category, gender,
      seededFromGithubAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }
  await batch.commit();
}

async function sendAdminPush(title, body) {
  try {
    const admin = await auth.getUserByEmail(ADMIN_EMAIL);
    const snapshot = await db.collection("users").doc(admin.uid)
      .collection("notificationTokens").limit(20).get();
    const tokens = snapshot.docs.map((doc) => doc.get("token"))
      .filter((token) => typeof token === "string" && token.length > 20);
    if (tokens.length === 0) return;
    await getMessaging().sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: { type: "admin_request" },
    });
  } catch (error) {
    console.error("sendAdminPush failed", error);
  }
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

function publicBillingStatus(data = {}, trial = {}) {
  const manualPremiumActive = Boolean(data.manualPremiumActive);
  return {
    active: manualPremiumActive || Boolean(data.active),
    status: manualPremiumActive ? "complimentary" : (data.status || "inactive"),
    plan: data.plan || null,
    currentPeriodEnd: data.currentPeriodEnd || null,
    cancelAtPeriodEnd: Boolean(data.cancelAtPeriodEnd),
    lastInvoiceStatus: data.lastInvoiceStatus || null,
    canManage: Boolean(data.creemCustomerId),
    trialActive: Boolean(trial.trialActive),
    trialEndsAt: trial.trialEndsAtEpochSeconds || null,
    serverNow: trial.serverNowEpochSeconds || null,
  };
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
    await sendAdminPush("בקשת ערוץ חדשה", `${name} ביקש/ה להוסיף ערוץ`);
    return res.json({ ok: true });
  } catch (error) {
    console.error("submitChannelRequest failed", error);
    return res.status(502).json({
      ok: false,
      message: "Unable to save the channel request",
    });
  }
});

// Manual Premium requests are intentionally server-owned. The Android client
// can submit a request, but only an administrator can view or resolve it.
export const submitPremiumRequest = onRequest({
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
  const phone = cleanSingleLine(req.body?.phone, 40);
  const contactEmail = cleanSingleLine(req.body?.contactEmail, 254).toLowerCase();
  const plan = normalizePlan(cleanSingleLine(req.body?.plan, 16));
  const accountEmail = cleanSingleLine(decoded.email, 254).toLowerCase();
  if (
    !name
    || !phone
    || !plan
    || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contactEmail)
    || !accountEmail
  ) {
    return res.status(400).json({
      ok: false,
      message: "יש למלא שם, טלפון, כתובת מייל ומסלול תקינים",
    });
  }

  try {
    const rateLimit = await acquirePremiumRequestAttempt(decoded.uid);
    if (!rateLimit.acquired) {
      res.set(
        "Retry-After",
        String(Math.max(1, Math.ceil(rateLimit.retryAfterMs / 1000))),
      );
      return res.status(429).json({
        ok: false,
        code: "PREMIUM_REQUEST_RATE_LIMITED",
        message: "כבר נשלחה בקשה לאחרונה. נסה שוב בעוד כמה דקות",
      });
    }

    const requestRef = db.collection("premiumRequests").doc(decoded.uid);
    const existing = await requestRef.get();
    if (existing.exists && existing.get("status") === "pending") {
      return res.status(409).json({
        ok: false,
        code: "PREMIUM_REQUEST_PENDING",
        message: "כבר קיימת בקשת Premium ממתינה",
      });
    }
    const requestVersion = randomUUID();
    const details = MANUAL_PLAN_DETAILS[plan];
    await requestRef.set({
      ownerUid: decoded.uid,
      accountEmail,
      contactEmail,
      name,
      phone,
      plan,
      priceUsd: details.priceUsd,
      status: "pending",
      requestVersion,
      submittedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    await sendAdminPush("בקשת Premium חדשה", `${name} ביקש/ה מסלול ${details.label}`);
    return res.json({ ok: true, requestVersion });
  } catch (error) {
    console.error("submitPremiumRequest failed", error);
    return res.status(502).json({
      ok: false,
      message: "לא ניתן לשמור את בקשת התשלום כרגע",
    });
  }
});

export const listPremiumRequests = onRequest({
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
    // Sort in memory so the endpoint works without requiring a composite index.
    const includeHistory = String(req.query?.history || "") === "1";
    let query = db.collection("premiumRequests");
    if (!includeHistory) query = query.where("status", "==", "pending");
    const snapshot = await query.limit(100).get();
    const requests = snapshot.docs.map((document) => {
      const data = document.data();
      const submittedAt = data.submittedAt?.toDate?.();
      return {
        id: document.id,
        version: cleanSingleLine(data.requestVersion, 64),
        accountEmail: cleanSingleLine(data.accountEmail, 254),
        contactEmail: cleanSingleLine(data.contactEmail, 254),
        name: cleanSingleLine(data.name, 120),
        phone: cleanSingleLine(data.phone, 40),
        plan: normalizePlan(data.plan) || "month",
        priceUsd: cleanSingleLine(data.priceUsd, 20),
        status: cleanSingleLine(data.status, 16) || "pending",
        requestedAt: submittedAt instanceof Date ? submittedAt.toISOString() : "",
      };
    }).sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
    return res.json({ ok: true, requests });
  } catch (error) {
    console.error("listPremiumRequests failed", error);
    return res.status(502).json({
      ok: false,
      message: "לא ניתן לטעון בקשות Premium כרגע",
    });
  }
});

export const resolvePremiumRequest = onRequest({
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
    return res.status(400).json({ ok: false, message: "בקשת Premium לא תקינה" });
  }

  try {
    const requestRef = db.collection("premiumRequests").doc(requestId);
    const result = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(requestRef);
      if (!snapshot.exists) return { found: false, versionMatches: false };
      if (snapshot.get("requestVersion") !== requestVersion) {
        return { found: true, versionMatches: false };
      }
      const status = snapshot.get("status") || "pending";
      if (status !== "pending") {
        return { found: true, versionMatches: true, status, alreadyResolved: status === resolution };
      }
      if (resolution === "approved") {
        const ownerUid = cleanSingleLine(snapshot.get("ownerUid"), 128);
        if (!ownerUid) throw new Error("Premium request has no owner");
        transaction.set(billingRef(ownerUid), {
          manualPremiumActive: true,
          manualPremiumGrantedAt: FieldValue.serverTimestamp(),
          manualPremiumGrantedFor: cleanSingleLine(snapshot.get("accountEmail"), 254),
          manualPremiumPlan: normalizePlan(snapshot.get("plan")) || "month",
          manualPremiumSource: "manual_email",
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
      transaction.update(requestRef, {
        status: resolution,
        resolvedAt: FieldValue.serverTimestamp(),
        resolvedByUid: decoded.uid,
        updatedAt: FieldValue.serverTimestamp(),
      });
      return { found: true, versionMatches: true, status: resolution, alreadyResolved: false };
    });
    if (!result.found) return res.status(404).json({ ok: false, message: "בקשת Premium לא נמצאה" });
    if (!result.versionMatches) return res.status(409).json({ ok: false, message: "הבקשה השתנתה; טען מחדש" });
    if (result.status && result.status !== resolution) {
      return res.status(409).json({ ok: false, message: "הבקשה כבר טופלה" });
    }
    return res.json({ ok: true, alreadyResolved: Boolean(result.alreadyResolved) });
  } catch (error) {
    console.error("resolvePremiumRequest failed", error);
    return res.status(502).json({ ok: false, message: "לא ניתן לעדכן את בקשת Premium" });
  }
});

export const listApprovedChannels = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 10,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "GET") return res.status(405).json({ ok: false, message: "GET required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  try {
    await ensureApprovedChannelsSeeded();
    const snapshot = await db.collection("approvedChannels").limit(500).get();
    const channels = snapshot.docs.map((document) => {
      const data = document.data();
      return {
        youtubeChannelId: cleanSingleLine(data.youtubeChannelId || document.id, 100),
        name: cleanSingleLine(data.name, 200),
        category: cleanSingleLine(data.category, 40) || "general",
        gender: cleanSingleLine(data.gender, 16) || "all",
      };
    }).filter((channel) => channel.youtubeChannelId && channel.name);
    res.set("Cache-Control", "private, no-store");
    return res.json({ ok: true, channels });
  } catch (error) {
    console.error("listApprovedChannels failed", error);
    return res.status(502).json({ ok: false, message: "לא ניתן לטעון ערוצים מאושרים" });
  }
});

export const registerNotificationToken = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 10,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  const token = cleanSingleLine(req.body?.token, 4096);
  if (token.length < 20) return res.status(400).json({ ok: false, message: "Token לא תקין" });
  const id = createHash("sha256").update(token).digest("hex");
  try {
    await db.collection("users").doc(decoded.uid).collection("notificationTokens").doc(id).set({
      token, updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return res.json({ ok: true });
  } catch (error) {
    console.error("registerNotificationToken failed", error);
    return res.status(502).json({ ok: false, message: "לא ניתן לרשום התראות" });
  }
});

export const upsertApprovedChannel = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res, true);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  if (!requireAdmin(decoded, res)) return;
  const youtubeChannelId = cleanSingleLine(req.body?.youtubeChannelId, 100);
  const name = cleanSingleLine(req.body?.name, 200);
  const category = cleanSingleLine(req.body?.category, 40);
  const gender = cleanSingleLine(req.body?.gender, 16) || "all";
  if (!/^UC[A-Za-z0-9_-]{20,}$/.test(youtubeChannelId)
    || !name || !CHANNEL_REQUEST_CATEGORIES.has(category)
    || !["all", "male", "female"].includes(gender)) {
    return res.status(400).json({ ok: false, message: "פרטי ערוץ לא תקינים" });
  }
  try {
    await db.collection("approvedChannels").doc(youtubeChannelId).set({
      youtubeChannelId, name, category, gender,
      updatedAt: FieldValue.serverTimestamp(),
      updatedByUid: decoded.uid,
    }, { merge: true });
    return res.json({ ok: true });
  } catch (error) {
    console.error("upsertApprovedChannel failed", error);
    return res.status(502).json({ ok: false, message: "לא ניתן לשמור את הערוץ" });
  }
});

export const removeApprovedChannel = onRequest({
  ...STATUS_HTTP_OPTIONS,
  maxInstances: 5,
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res, true);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;
  if (!requireAdmin(decoded, res)) return;
  const youtubeChannelId = cleanSingleLine(req.body?.youtubeChannelId, 100);
  if (!/^UC[A-Za-z0-9_-]{20,}$/.test(youtubeChannelId)) {
    return res.status(400).json({ ok: false, message: "מזהה ערוץ לא תקין" });
  }
  try {
    await db.collection("approvedChannels").doc(youtubeChannelId).delete();
    return res.json({ ok: true });
  } catch (error) {
    console.error("removeApprovedChannel failed", error);
    return res.status(502).json({ ok: false, message: "לא ניתן להסיר את הערוץ" });
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
    const includeHistory = String(req.query?.history || "") === "1";
    let query = db.collection("channelRequests");
    if (!includeHistory) query = query.where("status", "==", "pending");
    const snapshot = await query.limit(100).get();
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
        status: cleanSingleLine(data.status, 16) || "pending",
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
  secrets: [creemApiKey],
}, async (req, res) => {
  if (handleCors(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ ok: false, message: "POST required" });
  const decoded = await requireUser(req, res);
  if (!decoded) return;
  if (!requireVerifiedEmail(decoded, res)) return;

  const plan = normalizePlan(req.body?.plan);
  const productId = plan === "month"
    ? creemMonthlyProduct.value()
    : plan === "year"
      ? creemYearlyProduct.value()
      : null;
  if (!plan || !productId) {
    return res.status(400).json({ ok: false, message: "Unknown plan" });
  }
  if (!decoded.email) {
    return res.status(400).json({ ok: false, message: "Account email is required" });
  }

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

    const billing = checkoutLock.billing;
    if (managedBillingEntitled(billing)) {
      return res.status(409).json({
        ok: false,
        code: "SUBSCRIPTION_EXISTS",
        message: "A Premium subscription is already active",
      });
    }
    const checkout = await createCreemCheckout({
      apiKey: creemApiKey.value(),
      apiBase: creemApiBase.value(),
      productId,
      requestId: checkoutLock.idempotencyKey,
      email: decoded.email,
      firebaseUid: decoded.uid,
      plan,
      successUrl: creemSuccessUrl.value(),
    });
    const session = { id: checkout.id, url: checkout.checkout_url };
    await finishCheckoutLock(
      decoded.uid,
      checkoutLock.lockToken,
      session,
      plan,
      checkoutLock.idempotencyKey,
    );
    await billingRef(decoded.uid).set({
      billingProvider: "creem",
      checkoutProductId: productId,
    }, { merge: true });
    return res.json({ ok: true, checkoutUrl: checkout.checkout_url });
  } catch (error) {
    console.error("createCheckout failed", {
      message: error?.message,
      status: error?.status || null,
      traceId: error?.traceId || null,
    });
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
  secrets: [creemApiKey],
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
    const customerId = billing.creemCustomerId;
    if (!customerId) return res.status(404).json({
      ok: false,
      message: "No Creem customer was found",
    });
    const portalUrl = await createCreemCustomerPortal({
      apiKey: creemApiKey.value(),
      apiBase: creemApiBase.value(),
      customerId,
    });
    await finishPortalLock(
      decoded.uid,
      portalLock.lockToken,
    );
    return res.json({ ok: true, portalUrl });
  } catch (error) {
    console.error("createCustomerPortal failed", {
      message: error?.message,
      status: error?.status || null,
      traceId: error?.traceId || null,
    });
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
 * Fast entitlement snapshot that does not call Creem.
 *
 * A new account's 30-day trial must not depend on Creem availability or
 * configuration. Paid fields come only from the server-owned billing document;
 * billingStatus returns the authoritative server-owned billing snapshot.
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
    await finishStatusRefreshLock(
      decoded.uid,
      statusLock.lockToken,
    );
    return res.json({
      ok: true,
      billing: publicBillingStatus(trial.billing, trial),
    });
  } catch (error) {
    console.error("billingStatus failed", error);
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

function creemCustomerRef(customerId) {
  return db.collection("creemCustomers").doc(customerId);
}

async function uidForCreemEvent(update) {
  if (update.firebaseUid) {
    const exists = await auth.getUser(update.firebaseUid)
      .then(() => true)
      .catch(() => false);
    if (exists) return update.firebaseUid;
  }
  if (update.customerId) {
    const direct = await creemCustomerRef(update.customerId).get();
    const directUid = direct.get("uid");
    if (typeof directUid === "string" && directUid) return directUid;
    const billingMatch = await db.collectionGroup("billing")
      .where("creemCustomerId", "==", update.customerId)
      .limit(1)
      .get();
    if (!billingMatch.empty) {
      return billingMatch.docs[0].ref.parent.parent?.id || null;
    }
  }
  if (update.customerEmail) {
    return auth.getUserByEmail(update.customerEmail)
      .then((record) => record.uid)
      .catch(() => null);
  }
  return null;
}

async function applyCreemBillingEvent(uid, update) {
  if (!/^[A-Za-z0-9_-]{1,160}$/.test(update.eventId)) {
    throw new Error("Invalid Creem event identifier");
  }
  const eventRef = db.collection("billingWebhookEvents").doc(update.eventId);
  const reference = billingRef(uid);
  return db.runTransaction(async (transaction) => {
    const [eventSnapshot, billingSnapshot] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(reference),
    ]);
    if (eventSnapshot.exists) return "duplicate";
    const billing = billingSnapshot.data() || {};
    const lastEventCreated = positiveMillis(billing.lastCreemEventCreatedMillis);
    const stale = lastEventCreated > update.eventCreatedMillis;
    transaction.set(eventRef, {
      provider: "creem",
      eventType: update.eventType,
      eventCreatedMillis: update.eventCreatedMillis,
      ownerUid: uid,
      outcome: stale ? "stale" : "processed",
      processedAt: FieldValue.serverTimestamp(),
    });
    if (stale) return "stale";
    transaction.set(reference, {
      billingProvider: "creem",
      active: update.active,
      status: update.status,
      plan: update.plan,
      cancelAtPeriodEnd: update.cancelAtPeriodEnd,
      lastCreemEventId: update.eventId,
      lastCreemEventType: update.eventType,
      lastCreemEventCreatedMillis: update.eventCreatedMillis,
      updatedAt: FieldValue.serverTimestamp(),
      ...(update.customerId ? { creemCustomerId: update.customerId } : {}),
      ...(update.subscriptionId ? { creemSubscriptionId: update.subscriptionId } : {}),
      ...(update.checkoutId ? { checkoutSessionId: update.checkoutId } : {}),
      ...(update.productId ? { creemProductId: update.productId } : {}),
      ...(update.currentPeriodEnd !== null
        ? { currentPeriodEnd: update.currentPeriodEnd }
        : {}),
      ...(update.lastInvoiceStatus
        ? { lastInvoiceStatus: update.lastInvoiceStatus }
        : {}),
    }, { merge: true });
    if (update.customerId) {
      transaction.set(creemCustomerRef(update.customerId), {
        uid,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    }
    return "processed";
  });
}

export const creemWebhook = onRequest({
  ...WEBHOOK_HTTP_OPTIONS,
  secrets: [creemWebhookSecret],
}, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("POST required");
  const rawBody = req.rawBody || Buffer.from(JSON.stringify(req.body || {}));
  if (!verifyCreemWebhook(
    rawBody,
    req.get("creem-signature"),
    creemWebhookSecret.value(),
  )) {
    return res.status(401).send("Invalid webhook signature");
  }
  try {
    const event = JSON.parse(rawBody.toString("utf8"));
    const update = normalizeCreemEvent(event, {
      month: creemMonthlyProduct.value(),
      year: creemYearlyProduct.value(),
    });
    if (!update.plan) {
      console.warn("Ignoring Creem event for an unmanaged product", {
        eventId: update.eventId,
        eventType: update.eventType,
        productId: update.productId,
      });
      return res.json({ received: true, ignored: true });
    }
    const uid = await uidForCreemEvent(update);
    if (!uid) {
      console.error("Creem webhook user mapping failed", {
        eventId: update.eventId,
        eventType: update.eventType,
        customerId: update.customerId,
      });
      return res.status(500).send("Customer mapping failed");
    }
    const outcome = await applyCreemBillingEvent(uid, update);
    return res.json({ received: true, outcome });
  } catch (error) {
    console.error("creemWebhook processing failure", { message: error?.message });
    return res.status(500).send("Webhook processing failed");
  }
});
