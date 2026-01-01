# 🚀 Production Deployment Checklist

## 1. Required Environment Variables

Ensure these variables are set in your deployment environment (e.g., GitHub Secrets, Docker Environment, AWS Parameter Store).

### **Database & Infrastructure**

| Variable              | Description    | Example               |
| --------------------- | -------------- | --------------------- |
| `POSTGRESQL_URL`      | DB Host URL    | `prod-db.example.com` |
| `POSTGRESQL_PORT`     | DB Port        | `5432`                |
| `POSTGRESQL_USERNAME` | DB Username    | `stolink_admin`       |
| `POSTGRESQL_PASSWORD` | DB Password    | `super_secure_pw`     |
| `NEO4J_URI`           | Neo4j Bolt URI | `neo4j.example.com`   |
| `NEO4J_PORT`          | Neo4j Port     | `7687`                |
| `NEO4J_USERNAME`      | Neo4j Username | `neo4j`               |
| `NEO4J_PASSWORD`      | Neo4j Password | `neo4j_pw`            |

### **Security (Critical)**

| Variable               | Description                | Example                                            |
| ---------------------- | -------------------------- | -------------------------------------------------- |
| `JWT_SECRET`           | **Min 64 chars** for HS512 | `very_long_random_string_...`                      |
| `GOOGLE_CLIENT_ID`     | OAuth2 Client ID           | `1234...apps.googleusercontent.com`                |
| `GOOGLE_CLIENT_SECRET` | OAuth2 Client Secret       | `GOCSPX-...`                                       |
| `OAUTH2_REDIRECT_URI`  | Callback URI               | `https://api.stolink.com/login/oauth2/code/google` |

### **Application Config**

| Variable                   | Description           | Example                                       |
| -------------------------- | --------------------- | --------------------------------------------- |
| `CORS_ALLOWED_ORIGINS`     | Allowed Frontend URLs | `https://stolink.com,https://www.stolink.com` |
| `APP_STORAGE_BASE_PATH`    | File Upload Path      | `/app/storage`                                |
| `APP_AI_CALLBACK_BASE_URL` | AI Callback URL       | `https://api.stolink.com/api/internal/ai`     |

### **RabbitMQ**

| Variable                  | Description      |
| ------------------------- | ---------------- |
| `RABBITMQ_IMAGE_HOST`     | Image Queue Host |
| `RABBITMQ_IMAGE_USER`     | Image Queue User |
| `RABBITMQ_IMAGE_PASSWORD` | Image Queue PW   |
| `RABBITMQ_AGENT_HOST`     | Agent Queue Host |
| `RABBITMQ_AGENT_USER`     | Agent Queue User |
| `RABBITMQ_AGENT_PASSWORD` | Agent Queue PW   |

---

## 2. Validation Steps

1. **Health Check**: `GET /actuator/health` should return `{"status": "UP"}`.
2. **Schema Validation**: Monitor logs on startup. `ERROR: column ...` means schema mismatch.
3. **OAuth2 Test**: Try logging in with Google. Ensure redirected back to `CORS_ALLOWED_ORIGINS`.
