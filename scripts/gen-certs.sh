#!/usr/bin/env bash
# Tao keystore (server) + public certificate (client) bang keytool (JDK)
# Dung:  CHAT_CERT_PASSWORD='MatKhauManh' ./scripts/gen-certs.sh [host]
set -euo pipefail

HOST="${1:-localhost}"
: "${CHAT_CERT_PASSWORD:?Hay export CHAT_CERT_PASSWORD truoc}"

SERVER_DIR="zalo-server/certificates"
CLIENT_DIR="zalo-client/certificates"
mkdir -p "$SERVER_DIR" "$CLIENT_DIR"

# Buoc 1: tao keystore PKCS12 (Private Key + Certificate) -> chi o SERVER
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 \
  -keystore "$SERVER_DIR/server.p12" -storetype PKCS12 \
  -storepass "$CHAT_CERT_PASSWORD" -validity 365 \
  -dname "CN=$HOST, OU=Lab, O=ZaloClone, L=HCM, C=VN" \
  -ext "SAN=dns:$HOST,ip:127.0.0.1"

# Buoc 2: xuat Public Certificate -> phat cho CLIENT
keytool -exportcert -alias server \
  -keystore "$SERVER_DIR/server.p12" -storetype PKCS12 \
  -storepass "$CHAT_CERT_PASSWORD" \
  -rfc -file "$CLIENT_DIR/server.crt"

# Buoc 3: kiem tra
keytool -list -keystore "$SERVER_DIR/server.p12" -storetype PKCS12 \
  -storepass "$CHAT_CERT_PASSWORD"
echo "OK: server.p12 (giu o server), server.crt (copy cho client)"
