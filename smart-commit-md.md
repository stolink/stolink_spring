# Smart Commit 수동 실행 가이드

## 현재 상태

- **브랜치**: `deploytest` ✅ (push 허용)
- **Target Branch**: `dev`

## 스테이징된 변경사항

```
 .github/workflows/deploy.yml     | 2 +-
 .github/workflows/deploy_dev.yml | 2 +-
 .gitignore                       | 3 ++-
 4 files changed, 4 insertions(+), 3 deletions(-)
```

### 변경 내용 요약

- `deploy.yml`: `deploytest` 브랜치 제거, RabbitMQ 환경변수 IMAGE 접두사 적용, SSM 명령어 단일 블록으로 통합
- `deploy_dev.yml`: `develop` → `dev` 브랜치명 변경, RabbitMQ 환경변수 IMAGE 접두사 적용, SSM 명령어 단일 블록으로 통합
- `.gitignore`: smart-commit-md.md 제외 설정 복원

---

## 1단계: 커밋

```cmd
git commit -m "fix: 배포 워크플로우 브랜치 설정 및 RabbitMQ 환경변수 수정"
```

---

## 2단계: 푸시

```cmd
git push origin deploytest
```

---

## 3단계 이후: PR 처리

> 📌 **참고**: 나머지 단계(PR 확인, 생성/업데이트, 이슈 연결 등)는 아래 워크플로우를 참고하세요.
>
> **워크플로우 파일**: [.agent/workflows/smart-commit.md](.agent/workflows/smart-commit.md)
>
> - **3단계**: Target Branch 결정
> - **4단계**: PR 존재 여부 확인 (`gh pr view --json url,state`)
> - **4-A**: PR 신규 생성 (이슈 연결 포함)
> - **4-B**: 기존 PR 업데이트
> - **5단계**: 최종 보고

---

## 빠른 PR 확인 명령어

```cmd
gh pr view --json url,state
```

**결과에 따라:**

- URL 있음 → 푸시만으로 PR 자동 업데이트됨
- 비어있음 → PR 신규 생성 필요 (워크플로우 4-A 참고)
