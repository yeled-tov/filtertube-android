#!/usr/bin/env bash
# בונה הערות שחרור בעברית מתוך הודעות הקומיטים שנוספו מאז ה-Release הקודם,
# וכותב אותן ל-release-notes.md. את כותרת ה-Release הוא מייצא ב-RELEASE_TITLE.
#
# שימוש:  release-notes.sh <stable|test> <מספר בנייה> <שם ענף>
set -uo pipefail

MODE="${1:-stable}"
RUN="${2:-0}"
BRANCH="${3:-main}"

HEADING="### מה השתנה"

VERSION=$(grep -E '^versionName=' version.properties 2>/dev/null | head -1 | cut -d= -f2 | tr -d ' \r')
[ -z "$VERSION" ] && VERSION="1.0.0"

# מול איזו גרסה משווים — תלוי למי ההערות מיועדות.
#
# לבודק: הגרסה הקודמת שהוא התקין היא האחרונה מכל סוג, יציבה או טסט.
#
# ללקוח: הגרסה הקודמת שלו היא היציבה האחרונה בלבד. זה לא פרט טכני — כשהשוואנו
# גם מול תגי test, כל העבודה שנבדקה בערוץ הבדיקות כבר "נספרה", ורשימת השינויים
# של השחרור ליציב יצאה כמעט ריקה. בדיוק זה קרה ב-build-161: גרסה 1.2.0 יצאה
# ללקוחות עם שורה אחת, למרות עשרים ומשהו קומיטים.
if [ "$MODE" = "test" ]; then
  PREV=$(git tag -l 'build-*' 'test-*' --sort=-creatordate 2>/dev/null | head -n 1)
else
  PREV=$(git tag -l 'build-*' --sort=-creatordate 2>/dev/null | head -n 1)
fi

if [ -n "$PREV" ]; then
  RAW=$(git log --no-merges --pretty='%s' "${PREV}..HEAD" 2>/dev/null)
else
  RAW=$(git log --no-merges --pretty='%s' -20 2>/dev/null)
fi

# מסננים רעש שלא מעניין משתמש: בקשות ערוצים אוטומטיות, קומיטים של מיזוג וכו'.
CHANGES=$(printf '%s\n' "$RAW" \
  | grep -v -e '^channel request:' -e '\[skip ci\]' -e '^Merge ' \
  | sed '/^[[:space:]]*$/d' \
  | sed 's/^/- /')

# CHANGELOG.md גובר על כותרות הקומיטים כשיש בו קטע לגרסה הזו.
#
# כותרת קומיט נכתבת למפתח ("תקרת השדרוג: הגרסאות האחרונות שעובדות עם AGP
# 8.13"), ולמשתמש היא לא אומרת כלום. הקטע ב-CHANGELOG נכתב עבורו, מקובץ
# לפי תוקן/מהיר יותר/חדש — וזה מה שהוא רואה בדף העדכונים בתוך האפליקציה.
if [ -f CHANGELOG.md ]; then
  CURATED=$(awk -v v="## ${VERSION}" '
    $0 == v { inside = 1; next }
    inside && /^## / { exit }
    inside { print }
  ' CHANGELOG.md | sed '/^[[:space:]]*$/d')
  if [ -n "$CURATED" ]; then
    CHANGES="$CURATED"
    # לקטע מ-CHANGELOG יש כבר כותרות משנה משלו (תוקן / מהיר יותר / חדש),
    # אז הכותרת הכללית מיותרת ורק יוצרת שתי כותרות זו מעל זו.
    HEADING=""
  fi
fi

[ -z "$CHANGES" ] && CHANGES="- שיפורים ותיקונים כלליים"

if [ "$MODE" = "test" ]; then
  TITLE="בדיקה ${VERSION} (בנייה ${RUN})"
  cat > release-notes.md <<EOF
## גרסת בדיקה — FilterTube ${VERSION}
**בנייה ${RUN}** · ענף \`${BRANCH}\`

> ⚠️ גרסה זו נועדה לבדיקה בלבד ואינה משוחררת ללקוחות.
> היא מגיעה רק למכשירים שהפעילו **"ערוץ בדיקות"** בהגדרות.

${HEADING}
${CHANGES}

---
הורידו את \`FilterTube-test.apk\` למטה.
אחרי שהבדיקה עוברת — ממזגים ל-\`main\` כדי לשחרר ללקוחות.
EOF
else
  TITLE="גרסה ${VERSION} (בנייה ${RUN})"
  cat > release-notes.md <<EOF
## FilterTube ${VERSION}
**בנייה ${RUN}**

${HEADING}
${CHANGES}

---
### התקנה
הורידו את \`FilterTube.apk\` למטה והתקינו על המכשיר.
אם אנדרואיד מבקש — אשרו התקנה ממקור לא ידוע.
EOF
fi

echo "RELEASE_TITLE=${TITLE}" >> "${GITHUB_ENV:-/dev/null}"
echo "--- release-notes.md ---"
cat release-notes.md
