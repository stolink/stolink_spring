#!/bin/bash
ISSUE_NUM=$1
BRANCH_SUFFIX=$2
MANAGEMENT_REPO="stolink/stolink-manage"
PROJECT_NUMBER=1
PROJECT_ID="PVT_kwDODvp_7s4BLZVL"
STATUS_FIELD_ID="PVTSSF_lADODvp_7s4BLZVLzg6-5Vg"
IN_PROGRESS_OPTION_ID="47fc9ee4"

# PTY 에러 방지 - 핵심 설정

export GH_FORCE_TTY=0
export GH_NO_UPDATE_NOTIFIER=1
export GH_PROMPT_DISABLED=1
export NO_COLOR=1
export TERM=dumb

# 인자 없으면 목록 출력

if [ -z "$ISSUE_NUM" ]; then
echo "📋 작업 가능한 이슈:"
ITEMS=$(gh project item-list $PROJECT_NUMBER --owner stolink --format json --limit 20 2>/dev/null || echo '{"items":[]}')
  echo "$ITEMS" | jq -r '.items[] | select(.status == "Ready" or .status == "Open" or .status == null) | " \(.content.number). \(.content.title)"' 2>/dev/null
echo ""
echo "👉 /start-work <번호> [영문이름]"
exit 0
fi

# 이슈 정보 조회

ISSUE_DATA=$(gh issue view "$ISSUE_NUM" --repo "$MANAGEMENT_REPO" --json title,labels 2>/dev/null)
if [ -z "$ISSUE_DATA" ]; then
echo "❌ 이슈 #$ISSUE_NUM 조회 실패"
exit 1
fi

TITLE=$(echo "$ISSUE_DATA" | jq -r .title)
if [ -z "$TITLE" ] || [ "$TITLE" == "null" ]; then
echo "❌ 이슈 정보 없음"
exit 1
fi

# 브랜치 prefix 결정

IS_BUG=$(echo "$ISSUE_DATA" | jq -r '.labels[]?.name // empty' 2>/dev/null | grep -i "bug" || true)
if [ -n "$IS_BUG" ]; then
PREFIX="fix"
else
PREFIX="feature"
fi

# 브랜치 이름

if [ -n "$BRANCH_SUFFIX" ]; then
SAFE_SUFFIX=$(echo "$BRANCH_SUFFIX" | sed -e 's/[^a-zA-Z0-9-]//g' | tr '[:upper:]' '[:lower:]')
BRANCH_NAME="${PREFIX}/${ISSUE_NUM}-${SAFE_SUFFIX}"
else
  BRANCH_NAME="${PREFIX}/${ISSUE_NUM}"
fi

# 브랜치 생성/이동

if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
  git checkout "$BRANCH_NAME" >/dev/null 2>&1
echo "✅ $BRANCH_NAME (기존)"
else
  git checkout -b "$BRANCH_NAME" >/dev/null 2>&1
echo "✅ $BRANCH_NAME (신규)"
fi
echo "📝 $TITLE"

# 백그라운드 작업 - 완전히 분리

(
export GH_FORCE_TTY=0
export GH_NO_UPDATE_NOTIFIER=1
export GH_PROMPT_DISABLED=1
export NO_COLOR=1
export TERM=dumb

gh issue edit "$ISSUE_NUM" --repo "$MANAGEMENT_REPO" --add-assignee "@me" 2>/dev/null || true

ITEM_ID=$(gh project item-list $PROJECT_NUMBER --owner stolink --format json 2>/dev/null | jq -r ".items[] | select(.content.number == $ISSUE_NUM) | .id" 2>/dev/null)
  if [ -n "$ITEM_ID" ] && [ "$ITEM_ID" != "null" ]; then
gh project item-edit --id "$ITEM_ID" --project-id "$PROJECT_ID" --field-id "$STATUS_FIELD_ID" --single-select-option-id "$IN_PROGRESS_OPTION_ID" 2>/dev/null || true
fi
) </dev/null >/dev/null 2>&1 &
disown 2>/dev/null || true
