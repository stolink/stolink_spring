---
description: 변경사항 분석, 커밋, 푸시 후 PR 생성/업데이트
---

// turbo-all

## 0. 브랜치 확인

```bash
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
[[ "$CURRENT_BRANCH" =~ ^(main|dev)$ ]] && echo "❌ $CURRENT_BRANCH 직접 push 금지" && exit 1
```

## 1. 변경사항 확인 & 커밋

```bash
git status && git add . && git diff --staged --stat
# 변경사항 있으면 커밋
git commit -m "<type>(<scope>): <설명>"
git push origin $CURRENT_BRANCH
```

## 2. Target Branch 결정

```bash
[[ "$CURRENT_BRANCH" == hotfix/* ]] && TARGET_BRANCH="main" || TARGET_BRANCH="dev"
```

## 3. PR 확인

```bash
export PATH="/opt/homebrew/bin:$PATH"
PR_STATE=$(gh pr view --json state --jq .state 2>/dev/null || echo "NONE")
```

## 4. PR 처리

### 4-A. PR 없으면 생성

```bash
git fetch origin $TARGET_BRANCH
# .pr_body_temp.md 작성 (변경사항, 파일목록, 체크리스트)
# 브랜치명에서 이슈번호 추출: feature/123-foo → 123
ISSUE_NUM=$(echo "$CURRENT_BRANCH" | grep -oE '/[0-9]+(-|$)' | tr -d '/-')
# 이슈 없으면 stolink/stolink-manage에 생성
gh pr create --title "<제목>" --body-file .pr_body_temp.md --base $TARGET_BRANCH
rm .pr_body_temp.md
```

### 4-B. PR 있으면 업데이트

```bash
gh pr edit --title "<제목>" --body-file .pr_body_temp.md
```

## 5. 보고

| 항목    | 값                 |
| ------- | ------------------ |
| 브랜치  | $CURRENT_BRANCH    |
| PR 상태 | 신규/업데이트/없음 |
| Target  | $TARGET_BRANCH     |
