// בונה את חלק ההורדות של האתר ב-Firebase Hosting מתוך ה-Releases של GitHub.
//
// ## למה זה קיים
// האפליקציה, דף ההורדה ואתר filterphone.com מושכים כולם את ה-APK ואת מספרי
// הגרסאות ישירות מ-GitHub, בלי הזדהות. ברגע שהמאגר יהפוך לפרטי כל אחד מהם
// יקבל 404: האפליקציה תפסיק לגלות עדכונים, ובאתר ייעלם כפתור ההורדה.
//
// הפתרון הוא מראה: כל מה שהיה ציבורי ב-GitHub מועתק ל-Firebase Hosting,
// שנשאר ציבורי בין אם המאגר פרטי ובין אם לא. הסקריפט הזה רץ **לפני כל
// פריסת hosting**, ולכן כל פריסה — בין אם היא באה משחרור גרסה ובין אם
// מפריסת שרת — מייצרת אתר שלם. זה קריטי: פריסת hosting מחליפה את כל האתר,
// וכל job שהיה בונה רק את החלק שלו היה מוחק את מה שה-job השני העלה.
//
// ההזדהות היא GITHUB_TOKEN של ה-workflow, שעובד גם על מאגר פרטי.
//
// מה נוצר:
//   public/download/FilterTube.apk       — הגרסה היציבה האחרונה
//   public/download/FilterTube-test.apk  — גרסת הבדיקה האחרונה (אם יש)
//   public/releases.json                 — מה שהאפליקציה והאתר קוראים

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const SITE = "https://filter-tube-52d8e.web.app";
const REPO = process.env.GITHUB_REPOSITORY || "yeled-tov/filtertube-android";
const TOKEN = process.env.GITHUB_TOKEN || "";
const ROOT = process.env.HOSTING_ROOT || "public";
const OUT_DIR = path.join(ROOT, "download");

const headers = {
  Accept: "application/vnd.github+json",
  "User-Agent": "filtertube-hosting-mirror",
  ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
};

/** "- שורה" → "שורה". עוצר בקו המפריד, שאחריו מגיעות הוראות ההתקנה. */
function parseChanges(body) {
  return String(body || "")
    .split("\n")
    .slice(0, 400)
    .reduce((acc, line) => {
      if (acc.done) return acc;
      const t = line.trim();
      if (t.startsWith("---")) return { ...acc, done: true };
      if (t.startsWith("- ") || t.startsWith("* ")) {
        acc.out.push(t.slice(2).replace(/[`*_]/g, "").trim());
      }
      return acc;
    }, { out: [], done: false })
    .out.filter(Boolean)
    .slice(0, 15);
}

/**
 * הערות השחרור מתוך CHANGELOG.md, אם יש שם קטע לגרסה הזו.
 *
 * ## למה לא מגוף ה-Release
 * גוף ה-Release נבנה אוטומטית מכותרות הקומיטים כשאין קטע ב-CHANGELOG,
 * וכותרת קומיט נכתבת למפתח: "תיקון בנייה: חסר return ב-fromGithub" לא
 * אומרת ללקוח כלום. CHANGELOG.md נכתב עבורו, ולכן הוא קודם.
 *
 * זה חשוב במיוחד כאן: הקובץ הזה הוא מה שהאפליקציה קוראת כדי להציג
 * "מה חדש" בחלון העדכון, כלומר זה הטקסט שהלקוח באמת רואה.
 */
async function changelogFor(version) {
  if (!version) return null;
  const text = await readFile("CHANGELOG.md", "utf8").catch(() => "");
  if (!text) return null;
  const lines = text.split("\n");
  const start = lines.findIndex((line) => line.trim() === `## ${version}`);
  if (start < 0) return null;

  const items = [];
  for (let i = start + 1; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.startsWith("## ")) break;
    if (line.startsWith("### ") || line.trim() === "---") continue;
    const clean = (t) => t.replace(/[`*_]/g, "").trim();
    if (/^\s*[-*] /.test(line)) {
      items.push(clean(line.replace(/^\s*[-*] /, "")));
    } else if (items.length > 0 && line.trim() !== "") {
      // שורת המשך של פריט שנשבר לשתי שורות בקובץ.
      items[items.length - 1] = `${items[items.length - 1]} ${clean(line)}`;
    }
  }
  return items.filter(Boolean).slice(0, 20);
}

/** "גרסה 2.0.1 (בנייה 218)" → "2.0.1" */
function versionName(release) {
  const fromTitle = String(release.name || "").match(/\d+\.\d+(\.\d+)?/);
  if (fromTitle) return fromTitle[0];
  const fromBody = String(release.body || "").match(/\d+\.\d+(\.\d+)?/);
  return fromBody ? fromBody[0] : "";
}

function shape(release, prefix, assetName, publicPath, curated) {
  const asset = (release.assets || []).find((a) => a.name === assetName);
  return {
    build: Number(String(release.tag_name).replace(prefix, "")) || 0,
    tag: release.tag_name,
    versionName: versionName(release),
    changes: curated?.length ? curated : parseChanges(release.body),
    publishedAt: release.published_at,
    sizeBytes: asset?.size ?? null,
    downloads: asset?.download_count ?? 0,
    // כתובת יחסית לאתר ולא ל-GitHub: זו כל הנקודה.
    apkUrl: asset ? publicPath : null,
  };
}

async function main() {
  const res = await fetch(
    `https://api.github.com/repos/${REPO}/releases?per_page=100`,
    { headers, signal: AbortSignal.timeout(30_000) },
  );
  if (!res.ok) throw new Error(`GitHub API ${res.status}: ${await res.text()}`);
  const releases = await res.json();
  if (!Array.isArray(releases)) throw new Error("unexpected releases payload");

  const live = releases.filter((r) => !r.draft);
  const byBuild = (prefix) => (a, b) =>
    Number(String(b.tag_name).replace(prefix, "")) - Number(String(a.tag_name).replace(prefix, ""));

  const stableRelease = live
    .filter((r) => !r.prerelease && String(r.tag_name).startsWith("build-"))
    .sort(byBuild("build-"))[0];
  const testRelease = live
    .filter((r) => String(r.tag_name).startsWith("test-"))
    .sort(byBuild("test-"))[0];

  await mkdir(OUT_DIR, { recursive: true });

  const download = async (release, assetName, fileName) => {
    const asset = (release?.assets || []).find((a) => a.name === assetName);
    if (!asset) return false;
    // ה-asset עצמו מוגש דרך ה-API ולא דרך browser_download_url: רק המסלול
    // הזה מקבל את ה-Authorization, ורק הוא יעבוד כשהמאגר יהיה פרטי.
    const bin = await fetch(asset.url, {
      headers: { ...headers, Accept: "application/octet-stream" },
      redirect: "follow",
      signal: AbortSignal.timeout(120_000),
    });
    if (!bin.ok) throw new Error(`asset ${assetName}: HTTP ${bin.status}`);
    await writeFile(path.join(OUT_DIR, fileName), Buffer.from(await bin.arrayBuffer()));
    return true;
  };

  const gotStable = await download(stableRelease, "FilterTube.apk", "FilterTube.apk");
  const gotTest = await download(testRelease, "FilterTube-test.apk", "FilterTube-test.apk");

  // CHANGELOG.md הוא מקור האמת להערות השחרור, גם אם ה-Release עצמו כבר
  // פורסם עם כותרות קומיטים. כל ריצה של הסקריפט מיישרת את מה שהלקוח רואה.
  const stableNotes = gotStable ? await changelogFor(versionName(stableRelease)) : null;
  const testNotes = gotTest ? await changelogFor(versionName(testRelease)) : null;

  const payload = {
    updatedAt: new Date().toISOString(),
    stable: gotStable
      ? shape(stableRelease, "build-", "FilterTube.apk", "/download/FilterTube.apk", stableNotes)
      : null,
    test: gotTest
      ? shape(testRelease, "test-", "FilterTube-test.apk", "/download/FilterTube-test.apk", testNotes)
      : null,
    // סך ההורדות על פני כל הגרסאות — זה מה שהאתר מציג, ואחרי שהמאגר
    // יהפוך לפרטי הדפדפן לא יוכל לספור אותו בעצמו.
    totalDownloads: live.reduce(
      (sum, r) => sum + (r.assets || []).reduce((s, a) => s + (a.download_count || 0), 0),
      0,
    ),
    releaseCount: live.length,
    // ── אותו קובץ גם לאתר filterphone.com ─────────────────────────────
    // האתר קורא היום ישירות מ-api.github.com בדפדפן של המבקר, וזה נשבר
    // ברגע שהמאגר פרטי. הרשימה כאן שומרת בדיוק על המבנה של GitHub, כך
    // שהאתר צריך לשנות כתובת אחת בלבד ולא את כל צינור הנתונים.
    //
    // browser_download_url מצביע על ההעתק שלנו: מי שלוחץ "הורד" באתר
    // מקבל את הקובץ מהאתר, בלי לעבור דרך GitHub בכלל.
    releases: live.map((r) => ({
      tag_name: r.tag_name,
      name: r.name ?? null,
      body: r.body ?? null,
      published_at: r.published_at,
      html_url: r.html_url,
      prerelease: Boolean(r.prerelease),
      draft: false,
      assets: (r.assets || [])
        .filter((a) => String(a.name).toLowerCase().endsWith(".apk"))
        .map((a) => ({
          name: a.name,
          size: a.size,
          download_count: a.download_count ?? 0,
          // רק לגרסה האחרונה בכל ערוץ יש קובץ אצלנו; לשאר נשמרת הכתובת
          // המקורית, שמשמשת רק להצגת היסטוריה ולא להורדה בפועל.
          browser_download_url:
            r.tag_name === stableRelease?.tag_name && a.name === "FilterTube.apk"
              ? `${SITE}/download/FilterTube.apk`
              : r.tag_name === testRelease?.tag_name && a.name === "FilterTube-test.apk"
                ? `${SITE}/download/FilterTube-test.apk`
                : a.browser_download_url,
        })),
    })),
  };

  await writeFile(path.join(ROOT, "releases.json"), `${JSON.stringify(payload, null, 2)}\n`);

  console.log(
    `hosting mirror: stable=${payload.stable?.tag ?? "none"} test=${payload.test?.tag ?? "none"} ` +
      `downloads=${payload.totalDownloads}`,
  );
}

main().catch((error) => {
  console.error("build-hosting-downloads failed:", error.message);
  process.exit(1);
});
