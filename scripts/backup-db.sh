#!/bin/bash
# Daily Postgres backup: dump -> gzip -> upload to S3 -> prune old local copies.
# Intended to run via cron on the EC2 host (uses the instance's IAM role for S3 access).
set -euo pipefail

APP_DIR="/opt/teacherplatform"
BACKUP_DIR="$APP_DIR/backups"
S3_BUCKET="teacherplatform.503561455300"
S3_PREFIX="db-backups"
TIMESTAMP=$(date +%F_%H%M%S)
FILE="teacher_videos_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"
cd "$APP_DIR"

docker compose -f backend/docker-compose.prod.yml exec -T db \
  pg_dump -U teacher teacher_videos | gzip > "$BACKUP_DIR/$FILE"

aws s3 cp "$BACKUP_DIR/$FILE" "s3://${S3_BUCKET}/${S3_PREFIX}/${FILE}"

# Keep 7 days of local copies; add an S3 lifecycle rule on the bucket for long-term pruning.
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +7 -delete

echo "Backup complete: $FILE"
