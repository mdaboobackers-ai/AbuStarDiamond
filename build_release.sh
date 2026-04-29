#!/bin/bash
# ============================================================
# Abu Star Diamonds - RELEASE APK Build Script
# ============================================================
# Creates a signed release APK ready for distribution
# Run from: GoldsmithBilling project root

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
GOLD='\033[0;33m'
NC='\033[0m'

echo -e "${GOLD}"
echo "  ╔═══════════════════════════════════════════╗"
echo "  ║    ABU STAR DIAMONDS - RELEASE BUILD      ║"
echo "  ╚═══════════════════════════════════════════╝"
echo -e "${NC}"

KEYSTORE="abustar_release.jks"

# ── Step 1: Generate keystore if not exists ──────────────────
if [ ! -f "$KEYSTORE" ]; then
    echo -e "${YELLOW}No keystore found. Generating new keystore...${NC}"
    echo "(You'll need to enter details for your certificate)"
    echo ""
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -alias abustar \
        -dname "CN=Abu Star Diamonds, OU=Mobile, O=Abu Star Diamonds, L=Chennai, ST=Tamil Nadu, C=IN"
    echo ""
    echo -e "${GREEN}✓ Keystore generated: $KEYSTORE${NC}"
    echo -e "${RED}IMPORTANT: Back up $KEYSTORE safely! You need it for all future updates.${NC}"
fi

# ── Step 2: Get keystore passwords ──────────────────────────
echo ""
read -sp "$(echo -e ${YELLOW})Enter keystore password: $(echo -e ${NC})" STORE_PASS
echo
read -sp "$(echo -e ${YELLOW})Enter key password (press Enter if same as keystore): $(echo -e ${NC})" KEY_PASS
echo
if [ -z "$KEY_PASS" ]; then
    KEY_PASS=$STORE_PASS
fi

# ── Step 3: Inject signing config ───────────────────────────
# Create a temporary signing properties file
cat > signing.properties << EOF
storeFile=${PWD}/${KEYSTORE}
storePassword=${STORE_PASS}
keyAlias=abustar
keyPassword=${KEY_PASS}
EOF

# ── Step 4: Build release ────────────────────────────────────
echo ""
echo -e "${YELLOW}Building RELEASE APK...${NC}"

./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file="${PWD}/${KEYSTORE}" \
    -Pandroid.injected.signing.store.password="${STORE_PASS}" \
    -Pandroid.injected.signing.key.alias="abustar" \
    -Pandroid.injected.signing.key.password="${KEY_PASS}" \
    --stacktrace

rm -f signing.properties

APK_PATH="app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_PATH" ]; then
    SIZE=$(du -sh "$APK_PATH" | cut -f1)
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║      RELEASE BUILD SUCCESSFUL! ✓     ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  APK Path: ${YELLOW}$APK_PATH${NC}"
    echo -e "  APK Size: ${YELLOW}$SIZE${NC}"
    echo ""
    echo -e "${YELLOW}Distribution options:${NC}"
    echo "  1. Direct install:  adb install $APK_PATH"
    echo "  2. Share file:      Copy APK to phone via USB/WhatsApp"
    echo "  3. Google Play:     Build AAB instead: ./gradlew bundleRelease"
    echo ""
    echo -e "${RED}Remember to keep $KEYSTORE backed up securely!${NC}"
else
    echo -e "${RED}BUILD FAILED${NC}"
    exit 1
fi
