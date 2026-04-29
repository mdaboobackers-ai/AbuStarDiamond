#!/bin/bash
# ============================================================
# Abu Star Diamonds - APK Build Script
# ============================================================
# Run this from the GoldsmithBilling project root directory
# Requires: Android Studio + Android SDK installed

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
GOLD='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GOLD}"
echo "  ╔═══════════════════════════════════════════╗"
echo "  ║       ABU STAR DIAMONDS - BUILD APK       ║"
echo "  ║         Trust · Purity · Elegance          ║"
echo "  ╚═══════════════════════════════════════════╝"
echo -e "${NC}"

# ── Check prerequisites ──────────────────────────────────────
echo -e "${YELLOW}Checking prerequisites...${NC}"

if [ ! -f "local.properties" ]; then
    echo -e "${RED}ERROR: local.properties not found!${NC}"
    echo "Please create local.properties with your SDK path:"
    echo "  sdk.dir=/path/to/your/Android/Sdk"
    echo "(See local.properties.template for examples)"
    exit 1
fi

if [ ! -f "gradlew" ]; then
    echo -e "${RED}ERROR: gradlew not found. Are you in the project root?${NC}"
    exit 1
fi

chmod +x gradlew

# ── Build ────────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}Building DEBUG APK...${NC}"
./gradlew assembleDebug --stacktrace

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
    SIZE=$(du -sh "$APK_PATH" | cut -f1)
    echo ""
    echo -e "${GREEN}✓ BUILD SUCCESSFUL!${NC}"
    echo -e "${GREEN}  APK: $APK_PATH${NC}"
    echo -e "${GREEN}  Size: $SIZE${NC}"
    echo ""
    echo -e "${YELLOW}To install on connected device:${NC}"
    echo "  adb install $APK_PATH"
    echo ""
    echo -e "${YELLOW}To install via file transfer:${NC}"
    echo "  1. Copy $APK_PATH to your phone"
    echo "  2. Enable 'Install from Unknown Sources' in Settings"
    echo "  3. Tap the APK file to install"
else
    echo -e "${RED}BUILD FAILED - APK not found${NC}"
    exit 1
fi

# ── Optional: Install directly ───────────────────────────────
if command -v adb &> /dev/null; then
    DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)
    if [ "$DEVICES" -gt 0 ]; then
        echo ""
        read -p "$(echo -e ${YELLOW})Found Android device. Install now? (y/n): $(echo -e ${NC})" -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            adb install -r "$APK_PATH"
            echo -e "${GREEN}✓ Installed on device!${NC}"
        fi
    fi
fi
