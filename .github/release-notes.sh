#!/usr/bin/env bash
# בונה הערות שחרור בעברית מתוך הודעות הקומיטים שנוספו מאז ה-Release הקודם,
# וכותב אותן ל-release-notes.md. את כותרת ה-Release הוא מייצא ב-RELEASE_TITLE.
#
# שימוש:  release-notes.sh <stable|test> <מספר בנייה> <שם ענף>
set -uo pipefail

MODE="${1:-stable}"
RUN="${2:-0}"
BRANCH="${3:-main}"

VERSION=$(grep -E '^versionName=' version.properties 2>/dev/null | head -1 | cut -d= -f2 | tr -d ' \r')
[ -z "$VERSION" ] && VERSION="1.0.0"

# ה-Release האחרון מכל סוג — יציב או טסט. שניהם על אותו מונה, אז "האחרון
# שנוצר" הוא באמת הגרסה הקודמת שמשתמש יכול היה להתקין.
PREV=$(git tag -l 'build-*' 'test-*' --sort=-creatordate 2>/dev/null | head -n 1)

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
[ -z "$CHANGES" ] && CHANGES="- שיפורים ותיקונים כלליים"

if [ "$MODE" = "test" ]; then
  TITLE="בדיקה ${VERSION} (בנייה ${RUN})"
  cat > release-notes.md <<EOF
## גרסת בדיקה — FilterTube ${VERSION}
**בנייה ${RUN}** · ענף \`${BRANCH}\`

> ⚠️ גרסה זו נועדה לבדיקה בלבד ואינה משוחררת ללקוחות.
> היא מגיעה רק למכשירים שהפעילו **"ערוץ בדיקות"** בהגדרות.

### מה השתנה
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

### מה השתנה בגרסה הזו
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
