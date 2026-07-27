#!/bin/bash

set -e

APP_NAME="gather"
DEPLOY_DIR="/opt/gather"
CURRENT_JAR="$DEPLOY_DIR/gather.jar"
NEW_JAR="$DEPLOY_DIR/gather.jar.new"
BACKUP_JAR="$DEPLOY_DIR/gather-$(date +%Y%m%d-%H%M%S).jar.bak"
HEALTH_URL="http://localhost:8080/health"
ENV_FILE="/etc/gather/gather.env"
IMDS_BASE_URL="http://169.254.169.254/latest"
EXPECTED_INSTANCE_PROFILE_ROLE="GatherBackendProfileImageRole"

echo "===== Gather Backend Deploy Start ====="

echo "1. Check deploy directory"
if [ ! -d "$DEPLOY_DIR" ]; then
  echo "Deploy directory does not exist: $DEPLOY_DIR"
  exit 1
fi

echo "2. Check required S3 environment variables"
if ! sudo test -r "$ENV_FILE"; then
  echo "Environment file is not readable: $ENV_FILE"
  exit 1
fi

REQUIRED_S3_ENV_VARS=(
  "GATHER_AWS_REGION"
  "GATHER_AWS_S3_BUCKET"
  "GATHER_AWS_S3_PUBLIC_BASE_URL"
)

for variable_name in "${REQUIRED_S3_ENV_VARS[@]}"; do
  if ! sudo grep -Eq "^${variable_name}=.+$" "$ENV_FILE"; then
    echo "Required environment variable is missing: $variable_name"
    exit 1
  fi
done

echo "3. Check EC2 Instance Profile"
if ! IMDS_TOKEN=$(curl --fail --silent --show-error --max-time 3 \
  --request PUT "$IMDS_BASE_URL/api/token" \
  --header "X-aws-ec2-metadata-token-ttl-seconds: 60"); then
  echo "Unable to access EC2 Instance Metadata Service."
  exit 1
fi

if ! INSTANCE_PROFILE_ROLE=$(curl --fail --silent --show-error --max-time 3 \
  --header "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  "$IMDS_BASE_URL/meta-data/iam/security-credentials/"); then
  echo "EC2 Instance Profile is not attached."
  exit 1
fi

if [ -z "$INSTANCE_PROFILE_ROLE" ]; then
  echo "EC2 Instance Profile role name is empty."
  exit 1
fi

if [ "$INSTANCE_PROFILE_ROLE" != "$EXPECTED_INSTANCE_PROFILE_ROLE" ]; then
  echo "Unexpected EC2 Instance Profile role: $INSTANCE_PROFILE_ROLE"
  echo "Expected role: $EXPECTED_INSTANCE_PROFILE_ROLE"
  exit 1
fi

echo "4. Check new jar"
if [ ! -f "$NEW_JAR" ]; then
  echo "New jar file does not exist: $NEW_JAR"
  exit 1
fi

echo "5. Backup current jar"
if [ -f "$CURRENT_JAR" ]; then
  cp "$CURRENT_JAR" "$BACKUP_JAR"
  echo "Backup created: $BACKUP_JAR"
else
  echo "Current jar does not exist. Skip backup."
fi

echo "6. Replace jar"
mv "$NEW_JAR" "$CURRENT_JAR"

echo "7. Restart systemd service"
sudo systemctl restart "$APP_NAME"

echo "8. Wait for application startup"
sleep 90

echo "9. Health check"
if curl --fail --max-time 10 "$HEALTH_URL"; then
  echo ""
  echo "Health check success"
  echo "===== Gather Backend Deploy Success ====="
else
  echo ""
  echo "Health check failed"

  if [ -f "$BACKUP_JAR" ]; then
    echo "Rollback to backup jar"
    cp "$BACKUP_JAR" "$CURRENT_JAR"
    sudo systemctl restart "$APP_NAME"
    sleep 90

    if curl --fail --max-time 10 "$HEALTH_URL"; then
      echo ""
      echo "Rollback success"
    else
      echo ""
      echo "Rollback failed"
    fi
  fi

  echo "Recent application logs:"
  sudo journalctl -u "$APP_NAME" -n 100 --no-pager
  exit 1
fi
