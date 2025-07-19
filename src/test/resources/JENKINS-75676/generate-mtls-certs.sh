#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="$BASE_DIR/certs"
rm -rf "$CERT_DIR"
mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

echo "1. Generate Root CA"
openssl genrsa -out rootCA.key 4096
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 1825 -out rootCA.crt \
   -subj "/C=ES/L=Sevilla/O=TestOrg/CN=TestRootCA"

echo "2. Create Server Certificate (localhost, 127.0.0.1 SAN)"
cat > server_cert.cnf <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
C = ES
ST = Sevilla
L = Sevilla
O = TestOrg
OU = TestOrgEng
CN = localhost

[req_ext]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
EOF

openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -config server_cert.cnf
openssl x509 -req -in server.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial \
   -out server.crt -days 365 -sha256 -extensions req_ext -extfile server_cert.cnf

echo "3. Create Client Certificate (CN=client)"
cat > client_cert.cnf <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn

[dn]
C = ES
ST = Sevilla
L = Sevilla
O = TestOrg
OU = TestOrgEng
CN = client
EOF

openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr -config client_cert.cnf
openssl x509 -req -in client.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial \
   -out client.crt -days 365 -sha256

echo "4. Create PKCS12 for Java/Jenkins"
openssl pkcs12 -export -out client_keystore.p12 -inkey client.key -in client.crt -certfile rootCA.crt \
    -password pass:changeit -name client

echo "5. Copy certs for nginx docker use"
mkdir -p nginx
cp server.crt server.key rootCA.crt nginx/

echo "✅ All certificates generated in $CERT_DIR"
ls -l "$CERT_DIR"
ls -l "$CERT_DIR/nginx"

echo "🔥 CLIENT TEST (curl, must succeed):"
echo "    curl -vik --cert client.crt --key client.key --cacert rootCA.crt https://localhost:8443/"
echo "🔥 NEGATIVE TEST (no client cert, must fail):"
echo "    curl -vik --cacert rootCA.crt https://localhost:8443/"
