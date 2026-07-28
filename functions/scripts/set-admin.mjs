import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const PROJECT_ID = "filter-tube-52d8e";
const email = String(process.argv[2] || "").trim().toLowerCase();
const enabledText = String(process.argv[3] || "true").trim().toLowerCase();

if (
  !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)
  || !["true", "false"].includes(enabledText)
) {
  console.error("Usage: npm run set-admin -- owner@example.com [true|false]");
  process.exit(2);
}
const enabled = enabledText === "true";

if (getApps().length === 0) {
  initializeApp({
    credential: applicationDefault(),
    projectId: PROJECT_ID,
  });
}

const firebaseAuth = getAuth();
const user = await firebaseAuth.getUserByEmail(email);
if (enabled && !user.emailVerified) {
  throw new Error("The administrator account must verify its email first");
}

const claims = { ...(user.customClaims || {}) };
if (enabled) claims.admin = true;
else delete claims.admin;
await firebaseAuth.setCustomUserClaims(user.uid, claims);
if (!enabled) await firebaseAuth.revokeRefreshTokens(user.uid);

console.log(
  enabled
    ? `Administrator claim enabled for ${email}. Sign in again to refresh the token.`
    : `Administrator claim removed and sessions revoked for ${email}.`,
);
