# 🛠️ OAuth2 배포 환경 Access Denied 문제 해결

## 🚨 문제 진단

`https://d3bjnp58mvtfd4.cloudfront.net/oauth2/callback?success=true` 에서 `AccessDenied` 오류가 발생하는 것은, 백엔드에서 프론트엔드로 리다이렉트는 성공했지만 프론트엔드 서버(CloudFront + S3)가 `/oauth2/callback` 파일을 찾지 못했기 때문입니다.

**원인:** 이 프로젝트는 Single Page Application(SPA)이므로 `/oauth2/callback` 같은 경로는 클라이언트 사이드 JavaScript(React Router)가 처리하지, S3에 실제 파일이 존재하는 것이 아닙니다. S3는 해당 파일이 없으므로 `403 Forbidden` (또는 `404 Not Found`)를 반환하고, CloudFront가 이 오류를 그대로 사용자에게 전달합니다.

## ✅ 해결 방법: CloudFront 사용자 정의 오류 페이지 설정

S3가 403 또는 404 오류를 반환할 때 `index.html`을 200 OK 상태로 제공하도록 CloudFront를 설정해야 합니다. 이렇게 하면 React가 로드되어 해당 라우트를 처리할 수 있습니다.

### 방법 1: AWS 콘솔 사용 (권장)

1. [AWS Management Console](https://console.aws.amazon.com/cloudfront/)에 로그인합니다.
2. **CloudFront**로 이동합니다.
3. `d3bjnp58mvtfd4.cloudfront.net`에 해당하는 배포 ID를 클릭합니다.
4. **오류 페이지(Error pages)** 탭으로 이동합니다.
5. **사용자 정의 오류 응답 생성(Create custom error response)**을 클릭합니다.
6. **403 Forbidden 설정:**
   - **HTTP 오류 코드:** `403: Forbidden`
   - **오류 응답 사용자 정의:** `예`
   - **응답 페이지 경로:** `/index.html`
   - **HTTP 응답 코드:** `200: OK`
   - **생성**을 클릭합니다.
7. **404 Not Found도 설정 (권장):**
   - **사용자 정의 오류 응답 생성**을 다시 클릭합니다.
   - **HTTP 오류 코드:** `404: Not Found`
   - **오류 응답 사용자 정의:** `예`
   - **응답 페이지 경로:** `/index.html`
   - **HTTP 응답 코드:** `200: OK`
   - **생성**을 클릭합니다.

배포가 완료될 때까지 기다립니다 (상태: 진행 중 → 배포됨).

### 방법 2: AWS CLI 사용 (AWS 자격 증명이 설정된 경우)

터미널에서 다음 스크립트를 실행합니다:

```bash
#!/bin/bash
DOMAIN="d3bjnp58mvtfd4.cloudfront.net"

echo "🔍 $DOMAIN 에 해당하는 CloudFront 배포 검색 중..."
DIST_ID=$(aws cloudfront list-distributions --query "DistributionList.Items[?DomainName=='$DOMAIN'].Id" --output text)

if [ -z "$DIST_ID" ]; then
  echo "❌ 배포를 찾을 수 없습니다."
  exit 1
fi

echo "✅ 배포 ID 발견: $DIST_ID"

# 현재 설정 가져오기 (ETag 필요)
ETAG=$(aws cloudfront get-distribution-config --id $DIST_ID --query "ETag" --output text)

# 오류 응답 설정 정의
ERROR_CONFIG='{
  "Quantity": 2,
  "Items": [
    {
      "ErrorCode": 403,
      "ResponsePagePath": "/index.html",
      "ResponseCode": "200",
      "ErrorCachingMinTTL": 10
    },
    {
      "ErrorCode": 404,
      "ResponsePagePath": "/index.html",
      "ResponseCode": "200",
      "ErrorCachingMinTTL": 10
    }
  ]
}'

echo "🚀 사용자 정의 오류 응답 업데이트 중..."
aws cloudfront update-distribution \
  --id $DIST_ID \
  --if-match $ETAG \
  --distribution-config "file://<(aws cloudfront get-distribution-config --id $DIST_ID --query 'DistributionConfig' --output json | jq '.CustomErrorResponses = $ERROR_CONFIG')"

echo "✅ 업데이트 제출 완료! 배포가 완료될 때까지 기다려주세요."
```

## ⚠️ 참고 사항

- 프론트엔드 배포(Github Actions)가 `index.html`을 S3 버킷의 루트에 업로드하는지 확인하세요.
- 이 변경은 모든 "가상" 라우트 (예: `/project/123`, `/login` 등)에 적용되므로, 이제 어떤 페이지에서 새로고침해도 Access Denied 오류 대신 정상적으로 동작합니다.
