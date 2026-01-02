#!/bin/bash

# 🔍 Diagnostic Script for 502 Bad Gateway Issues
# This script helps diagnose why the Spring Boot application isn't responding

set -e

echo "🔍 Starting deployment diagnostics..."
echo ""

# Check if we can find the EC2 instance
echo "1️⃣ Finding EC2 instance..."
INSTANCE_ID=$(aws ec2 describe-instances \
  --filters "Name=tag:tag,Values=stolink-spring" "Name=instance-state-name,Values=running" \
  --query "Reservations[0].Instances[0].InstanceId" \
  --output text 2>/dev/null || echo "")

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ]; then
  echo "❌ Could not find EC2 instance with tag:tag=stolink-spring"
  echo "   Trying alternative tag Name=stolink-spring-ec2..."

  INSTANCE_ID=$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=stolink-spring-ec2" "Name=instance-state-name,Values=running" \
    --query "Reservations[0].Instances[0].InstanceId" \
    --output text 2>/dev/null || echo "")
fi

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ]; then
  echo "❌ Still could not find EC2 instance. Please check your EC2 tags."
  echo "   Run: aws ec2 describe-instances --query 'Reservations[].Instances[].[InstanceId,Tags[?Key==\`Name\`].Value|[0],State.Name]' --output table"
  exit 1
fi

echo "✅ Found instance: $INSTANCE_ID"
echo ""

# Function to run SSM command and get output
run_ssm_command() {
  local commands="$1"
  local description="$2"

  echo "📋 $description"

  COMMAND_ID=$(aws ssm send-command \
    --document-name "AWS-RunShellScript" \
    --instance-ids "$INSTANCE_ID" \
    --parameters "commands=[\"$commands\"]" \
    --timeout-seconds 30 \
    --query "Command.CommandId" \
    --output text)

  echo "   Command ID: $COMMAND_ID"
  echo "   Waiting for command to complete..."
  sleep 8

  OUTPUT=$(aws ssm get-command-invocation \
    --command-id "$COMMAND_ID" \
    --instance-id "$INSTANCE_ID" \
    --query "StandardOutputContent" \
    --output text 2>&1 || echo "Failed to get output")

  echo "$OUTPUT"
  echo ""
}

# 2. Check Docker container status
echo "2️⃣ Checking Docker containers..."
run_ssm_command "cd /home/ubuntu/stolink-project && sudo docker compose ps" "Container Status"

# 3. Get backend logs (using correct service name)
echo "3️⃣ Getting application logs..."
run_ssm_command "cd /home/ubuntu/stolink-project && sudo docker compose logs backend --tail 100" "Application Logs"

# 4. Check if application is responding locally
echo "4️⃣ Testing health endpoint from inside EC2..."
run_ssm_command "curl -s -o /dev/null -w 'HTTP Status: %{http_code}\n' http://localhost:8080/actuator/health || echo 'Connection failed'" "Local Health Check"

# 5. Check environment variables (redacted)
echo "5️⃣ Checking environment variables..."
run_ssm_command "if [ -f /home/ubuntu/stolink-project/.env ]; then cat /home/ubuntu/stolink-project/.env | grep -E '^(POSTGRESQL|NEO4J|JWT_SECRET|RABBITMQ|CORS)' | sed 's/=.*/=***REDACTED***/'; else echo '.env file not found'; fi" "Environment Variables"

# 6. Check disk space
echo "6️⃣ Checking disk space..."
run_ssm_command "df -h / && echo '' && docker system df" "Disk Space"

echo ""
echo "✅ Diagnostics complete!"
echo ""
echo "📌 Next steps:"
echo "   1. Review the logs above for error messages"
echo "   2. Check if the container is running (should show 'Up' status)"
echo "   3. Verify HTTP status from local health check (should be 200)"
echo "   4. Ensure all required environment variables are present"
echo ""
echo "📖 Full troubleshooting guide: /Users/dongha/.gemini/antigravity/brain/8a5fd248-640b-4f5d-a065-4e939c20f0e7/troubleshooting_502.md"
