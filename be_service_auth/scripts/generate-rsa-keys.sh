#!/usr/bin/env bash
# Tạo cặp khoá RSA 2048-bit (PKCS#8) cho AUTH-SERVICE ký JWT (RS256).
#   - private_key.pem : CHỈ AUTH-SERVICE giữ (không commit lên git)
#   - public_key.pem  : tham khảo / debug. Các service khác lấy public key qua JWKS:
#                       GET /api/v1/auth/.well-known/jwks.json
#
# Usage: ./scripts/generate-rsa-keys.sh [output_dir]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-$SCRIPT_DIR/../src/main/resources/keys}"
mkdir -p "$OUT_DIR"

if [[ -f "$OUT_DIR/private_key.pem" ]]; then
  echo "⚠️  $OUT_DIR/private_key.pem đã tồn tại – xoá thủ công nếu muốn tạo lại (mọi token cũ sẽ mất hiệu lực)."
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$OUT_DIR/private_key.pem"
openssl pkey -in "$OUT_DIR/private_key.pem" -pubout -out "$OUT_DIR/public_key.pem"
chmod 600 "$OUT_DIR/private_key.pem"

echo "✅ Đã tạo khoá tại: $OUT_DIR"
ls -l "$OUT_DIR"
