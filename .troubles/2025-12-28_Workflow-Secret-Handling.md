# AI Review Workflow Security Improvement

## Issue Description

GitHub Actions 워크플로우에서 API 키 누락 시에도 성공(`exit 0`)으로 처리되거나, 로그에 마스킹 처리가 명시되지 않는 보안/안전성 미흡 사항 발견. (AI 리뷰 지적 사항: ⚠️ 경고)

- 파일: `.github/workflows/ai-review.yml`
- 라인: 114-117
- 에러 유형: ⚠️ 경고

## Solution Strategy

API 키 누락 시 명시적으로 실패(`exit 1`)하도록 변경하고, `::add-mask::` 명령어를 통해 로그 보안을 강화.

### 변경 전

```bash
          if [ -z "$ANTHROPIC_API_KEY" ]; then
            echo "API Error: ANTHROPIC_API_KEY secret is missing in GitHub repository settings." > review_result.txt
            exit 0
          fi
```

### 변경 후

```bash
          if [ -z "$ANTHROPIC_API_KEY" ]; then
            echo "API Error: ANTHROPIC_API_KEY secret is missing in GitHub repository settings." > review_result.txt
            echo "error=API_KEY_MISSING" >> $GITHUB_OUTPUT
            exit 1
          fi

          echo "::add-mask::$ANTHROPIC_API_KEY"
```

## Outcome

- **상태**: ✅ 해결됨
- **빌드 결과**: N/A (워크플로우 스크립트 수정)
- **검증 방법**: 코드 리뷰 사항 반영 및 문법 검토.
