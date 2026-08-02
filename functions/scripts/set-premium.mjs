import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const PROJECT_ID = "filter-tube-52d8e";
const email = String(process.argv[2] || "").trim().toLowerCase();
const enabledText = String(process.argv[3] || "true").trim().toLowerCase();

if (
  !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)
  || !["true", "false"].includes(enabledText)
) {
  console.error("Usage: npm run set-premium -- owner@example.com [true|false]");
  process.exit(2);
}
const enabled = enabledText === "true";

if (getApps().length === 0) {
  initializeApp({
    credential: applicationDefault(),
    projectId: PROJECT_ID,
  });
}

const user = await getAuth().getUserByEmail(email);
if (enabled && !user.emailVerified) {
  throw new Error("The Premium account must verify its email first");
}

const billingRef = getFirestore()
  .collection("users")
  .doc(user.uid)
  .collection("billing")
  .doc("status");

await billingRef.set(
  enabled
    ? {
      manualPremiumActive: true,
      manualPremiumGrantedAt: FieldValue.serverTimestamp(),
      manualPremiumGrantedFor: email,
    }
    : {
      manualPremiumActive: FieldValue.delete(),
      manualPremiumGrantedAt: FieldValue.delete(),
      manualPremiumGrantedFor: FieldValue.delete(),
    },
  { merge: true },
);

console.log(
  enabled
    ? `Complimentary Premium enabled for ${email}.`
    : `Complimentary Premium removed for ${email}.`,
);
