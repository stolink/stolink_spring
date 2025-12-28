`stolink_fastapi_image` 리포지토리의 변수와 시크릿 정보를 요청한 대로 짧은 단어 중심의 역할로 정리한다.

---

### 1. Variables (공개 설정값)

| 이름                            | 값                              | 역할                       |
| ------------------------------- | ------------------------------- | -------------------------- |
| **AWS_BEDROCK_DEFAULT_REGION**  | `us-east-1`                     | Bedrock 서비스 리전        |
| **AWS_REGION**                  | `ap-northeast-2`                | 인프라 기본 리전           |
| **CLOUDFRONT_URL**              | `d8gj5l022kjd.cloudfront.net`   | 운영 CDN 도메인            |
| **CLOUDFRONT_URL_DEV**          | `d3bjnp58mvtfd4.cloudfront.net` | 개발 CDN 도메인            |
| **FRONTEND_S3_BUCKET_NAME**     | `stolink-web-prod-seoul`        | 운영 웹 버킷명             |
| **FRONTEND_S3_BUCKET_NAME_DEV** | `stolink-web-dev-seoul`         | 개발 웹 버킷명             |
| **MEDIA_S3_BUCKET_NAME**        | `stolink-media-prod-seoul`      | 운영 미디어 버킷명         |
| **MEDIA_S3_BUCKET_NAME_DEV**    | `stolink-media-dev-seoul`       | 개발 미디어 버킷명         |
| **NEO4J_PORT**                  | `7687`                          | Neo4j 접속 포트            |
| **POSTGRESQL_DATABASE**         | `stolink`                       | DB 스키마 명칭             |
| **POSTGRESQL_PORT**             | `5432`                          | DB 접속 포트               |
| **RABBITMQ_IMAGE_PORT**         | `5672`                          | 이미지 RabbitMQ 접속 포트  |
| **RABBITMQ_IMAGE_VHOST**        | `stolink`                       | 이미지 RabbitMQ 가상호스트 |

---

### 2. Secrets (민감 정보 형식)

| 이름                              | 형식 (Value Format)                    | 역할                           |
| --------------------------------- | -------------------------------------- | ------------------------------ |
| **AWS_BEDROCK_ACCESS_KEY_ID**     | `AKIA...` (대문자/숫자 20자)           | Bedrock 인증 ID                |
| **AWS_BEDROCK_SECRET_ACCESS_KEY** | `wJal...` (혼합 문자열 40자)           | Bedrock 인증 PW                |
| **GEMINI_API_KEY**                | `AIzaSy...` (혼합 문자열)              | Gemini API 인증                |
| **AWS_ROLE_S3FULL_ARN**           | `arn:aws:iam::...`                     | S3 권한 식별자                 |
| **AWS_ROLE_SSMFULL_ARN**          | `arn:aws:iam::...`                     | SSM 권한 식별자                |
| **GH_PAT**                        | `ghp_...` (토큰 형식)                  | GitHub API 인증                |
| **NEO4J_PASSWORD**                | `[텍스트 패스워드]`                    | Neo4j 접속 암호                |
| **NEO4J_URI**                     | `neo4j+s://...`                        | Neo4j 접속 경로                |
| **NEO4J_USERNAME**                | `[사용자 계정]`                        | Neo4j 접속 계정                |
| **POSTGRESQL_PASSWORD**           | `[텍스트 패스워드]`                    | DB 접속 암호                   |
| **POSTGRESQL_URL**                | `postgresql://...`                     | DB 접속 경로                   |
| **POSTGRESQL_USERNAME**           | `[사용자 계정]`                        | DB 접속 계정                   |
| **RABBITMQ_IMAGE_HOST**           | `10.0.x.x` (Private IPv4)              | 이미지 RabbitMQ EC2 Private IP |
| **RABBITMQ_IMAGE_USER**           | `[사용자 계정]`                        | 이미지 RabbitMQ 접속 계정      |
| **RABBITMQ_IMAGE_PASSWORD**       | `[텍스트 패스워드]`                    | 이미지 RabbitMQ 접속 암호      |
| **ALB_DNS_NAME**                  | `xxx.ap-northeast-2.elb.amazonaws.com` | Spring ALB DNS 이름            |

---
