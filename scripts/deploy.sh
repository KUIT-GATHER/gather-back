#!/bin/bash

set -e

APP_NAME="gather"
DEPLOY_DIR="/opt/gather"
CURRENT_JAR="$DEPLOY_DIR/gather.jar"
NEW_JAR="$DEPLOY_DIR/gather.jar.new"
BACKUP_JAR="$DEPLOY_DIR/gather-$(date +%Y%m%d-%H%M%S).jar.bak"
HEALTH_URL="http://localhost/health"

echo "===== Gather Backend Deploy Start ====="

echo "1. Check deploy directory"
if [ ! -d "$DEPLOY_DIR" ]; then
  echo "Deploy directory does not exist: $DEPLOY_DIR"
  exit 1
fi

echo "2. Check new jar"
if [ ! -f "$NEW_JAR" ]; then
  echo "New jar file does not exist: $NEW_JAR"
  exit 1
fi

echo "3. Backup current jar"
if [ -f "$CURRENT_JAR" ]; then
  cp "$CURRENT_JAR" "$BACKUP_JAR"
  echo "Backup created: $BACKUP_JAR"
else
  echo "Current jar does not exist. Skip backup."
fi

echo "4. Replace jar"
mv "$NEW_JAR" "$CURRENT_JAR"

echo "5. Restart systemd service"
sudo systemctl restart "$APP_NAME"

echo "6. Wait for application startup"
sleep 20

echo "7. Health check"
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
    sleep 20

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