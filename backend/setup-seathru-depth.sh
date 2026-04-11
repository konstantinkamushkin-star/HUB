#!/usr/bin/env bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$BACKEND_DIR/.env"
AI_MODELS="$BACKEND_DIR/ai-service/models"
DEFAULT_CKPT="$AI_MODELS/depth_anything_v2_vits.pth"

mkdir -p "$AI_MODELS"
touch "$ENV_FILE"

upsert_env() {
  local key="$1"
  local value="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    sed -i '' -E "s|^${key}=.*$|${key}=${value}|g" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

CKPT_PATH="${1:-$DEFAULT_CKPT}"
ENCODER="${SEATHRU_DEPTH_ENCODER:-vits}"
DEVICE="${SEATHRU_DEPTH_DEVICE:-auto}"

upsert_env "SEATHRU_DEPTHANYTHINGV2_CHECKPOINT" "$CKPT_PATH"
upsert_env "SEATHRU_DEPTH_ENCODER" "$ENCODER"
upsert_env "SEATHRU_DEPTH_DEVICE" "$DEVICE"

echo "Configured Sea-Thru depth in $ENV_FILE:"
echo "  SEATHRU_DEPTHANYTHINGV2_CHECKPOINT=$CKPT_PATH"
echo "  SEATHRU_DEPTH_ENCODER=$ENCODER"
echo "  SEATHRU_DEPTH_DEVICE=$DEVICE"

if [[ -f "$CKPT_PATH" ]]; then
  echo "Checkpoint found: $CKPT_PATH"
else
  echo "Checkpoint not found yet: $CKPT_PATH"
  echo "Place DepthAnythingV2 checkpoint there, then run:"
  echo "  ./start-neural-test-stack.sh"
fi
