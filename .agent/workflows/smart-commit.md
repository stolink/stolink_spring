---
description: 변경사항 분석, 커밋, 푸시 후 PR 상태를 확인하여 생성하거나 최신화합니다.
---

> **참고:** 커밋 컨벤션은 `CLAUDE.md`를 따릅니다.
> **언어:** 모든 결과 보고 및 PR 본문은 **한글**로 작성합니다.

// turbo-all

---

## 0. 브랜치 전략 준수 확인 (필수!)

**⚠️ 직접 push 금지 브랜치**: `main`, `develop`

```bash
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
```

| 현재 브랜치                      | push 가능? | 조치                                                                               |
| -------------------------------- | ---------- | ---------------------------------------------------------------------------------- |
| `main`                           | ❌ 금지    | "main에 직접 push할 수 없습니다. feature 브랜치를 생성하세요." 안내 후 **중단**    |
| `develop`                        | ❌ 금지    | "develop에 직접 push할 수 없습니다. feature 브랜치를 생성하세요." 안내 후 **중단** |
| `feature/*`, `fix/*`, `hotfix/*` | ✅ 허용    | 계속 진행                                                                          |

**main/develop에 있는 경우 → 새 브랜치 생성 제안**:

```bash
# 권장 명령어 안내
git checkout -b feature/<기능명>
# 또는
git checkout -b fix/<이슈설명>
```

---

## 1. 현재 상태 및 변경사항 확인

```bash
git status
git add .
git diff --staged --stat
```

- 스테이징된 변경사항이 있는지 확인
- 없으면 "커밋할 내용이 없습니다" 안내 후 **3단계로 건너뛰기**

---

## 2. 조건부 커밋 및 푸시

**변경사항이 있는 경우에만 실행**:

```bash
# Conventional Commit 메시지 생성 (diff 분석 기반)
git commit -m "<type>: <설명>" --no-verify

# 원격에 푸시
git push origin $CURRENT_BRANCH --no-verify
```

---

## 3. Target Branch 결정

| 현재 브랜치 패턴           | Target Branch |
| -------------------------- | ------------- |
| `hotfix/*`                 | `main`        |
| `feature/*`, `fix/*`, 기타 | `develop`     |

```bash
if [[ "$CURRENT_BRANCH" == hotfix/* ]]; then
  TARGET_BRANCH="main"
else
  TARGET_BRANCH="develop"
fi
```

---

## 4. PR 존재 여부 확인 (필수!)

```bash
PR_URL=$(gh pr view --json url,state --jq 'select(.state == "OPEN") | .url' 2>/dev/null || echo "")
```

| 결과     | 상태                                  |
| -------- | ------------------------------------- |
| URL 있음 | PR이 이미 존재 → **4-B로** (업데이트) |
| 비어있음 | PR 없음 → **4-A로** (생성)            |

---

## 4-A. PR 신규 생성 (PR이 없는 경우)

**반드시 실행해야 하는 단계**:

### Step 1: 원격과의 차이 확인

```bash
git fetch origin $TARGET_BRANCH
COMMITS=$(git log origin/$TARGET_BRANCH..$CURRENT_BRANCH --oneline)
```

- 커밋이 없으면: "base 브랜치 대비 새로운 커밋이 없습니다." 보고 후 종료

### Step 2: PR 본문 작성 (필수!)

`.pr_body_temp.md` 파일 생성:

```markdown
## 📋 변경 사항

<커밋 기반 변경 내용 요약 - 한글로 작성>

## 📁 변경된 파일

<파일 목록>

## ✅ 체크리스트

- [ ] 빌드 성공 확인 (`./gradlew build`)
- [ ] 로컬 테스트 완료

## 🔀 Merge 가이드

- Target: `<TARGET_BRANCH>`
- Squash and Merge 권장
```

### Step 3: PR 생성

```bash
gh pr create \
  --title "<종합된 변경 제목>" \
  --body-file .pr_body_temp.md \
  --base $TARGET_BRANCH
```

### Step 4: 정리

```bash
rm .pr_body_temp.md
```

---

## 4-B. 기존 PR 업데이트 (PR이 있는 경우)

### Step 1: 변경 내역 분석

```bash
git fetch origin $TARGET_BRANCH
git log origin/$TARGET_BRANCH..$CURRENT_BRANCH --oneline
```

### Step 2: PR 본문 재작성

`.pr_body_temp.md` 파일에 최신 변경사항 반영

### Step 3: PR 업데이트

```bash
gh pr edit \
  --title "<종합된 변경 제목>" \
  --body-file .pr_body_temp.md
```

### Step 4: 정리

```bash
rm .pr_body_temp.md
```

---

## 5. 최종 보고

반드시 아래 내용을 보고:

| 항목    | 값                              |
| ------- | ------------------------------- |
| 브랜치  | `$CURRENT_BRANCH`               |
| 커밋    | O / X (커밋 메시지)             |
| 푸시    | O / X                           |
| PR 상태 | 신규 생성 / 업데이트 / 변경없음 |
| PR URL  | `<URL>`                         |
| Target  | `$TARGET_BRANCH`                |

---

## ⚠️ 주의사항

1. **main/develop에 직접 push 금지** - feature 브랜치 사용 필수
2. **PR 본문 없이 생성 금지** - 항상 `.pr_body_temp.md` 작성 후 생성
3. **PR 존재 확인 필수** - gh pr view로 확인 후 생성/업데이트 결정
4. **변경사항 없어도 PR 상태 확인** - 기존 PR이 있으면 업데이트 가능

---

## 흐름도

```
시작
  │
  ▼
브랜치 확인 ──main/develop──▶ ❌ 중단 (새 브랜치 생성 안내)
  │
  ▼ (feature/fix/hotfix)
  │
변경사항 있음? ──No──▶ 3단계로 건너뛰기
  │
  ▼ Yes
커밋 & 푸시
  │
  ▼
Target 결정 (hotfix→main, 그 외→develop)
  │
  ▼
PR 존재? ──Yes──▶ 4-B: PR 업데이트
  │
  ▼ No
4-A: PR 신규 생성 (본문 필수!)
  │
  ▼
최종 보고
```
