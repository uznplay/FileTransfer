#!/bin/bash
# ============================================
# Build APK tự động (Linux/Mac)
# ============================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "   BUILD APK - File Transfer Android    "
echo "========================================"
echo ""

# Kiểm tra Java
echo "[1/4] Kiểm tra Java..."
if command -v java &> /dev/null; then
    echo "  => OK: $(java -version 2>&1 | head -1)"
else
    echo "[!] Lỗi: Không tìm thấy Java!"
    echo "    Cài đặt: sudo apt install openjdk-17-jdk (Linux)"
    echo "    Hoặc: brew install openjdk@17 (Mac)"
    exit 1
fi
echo ""

# Kiểm tra ANDROID_HOME
echo "[2/4] Kiểm tra Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    echo "  => Đặt ANDROID_HOME=$ANDROID_HOME"
fi

if [ ! -d "$ANDROID_HOME/platforms" ]; then
    echo "  => Android SDK chưa được cài đặt."
    echo "  => Cài đặt qua Android Studio hoặc dùng sdkmanager."
    echo "  => Hoặc dùng GitHub Actions để build trên cloud (không cần cài gì)"
fi
echo ""

# Cấp quyền gradlew
echo "[3/4] Cấp quyền gradlew..."
chmod +x gradlew 2>/dev/null || true
echo "  => OK"
echo ""

# Build
echo "[4/4] Build APK..."
echo "  => Đang build (có thể mất 2-5 phút)..."
./gradlew assembleDebug --no-daemon

# Kết quả
APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo ""
    echo "========================================"
    echo "   BUILD THÀNH CÔNG!                    "
    echo "========================================"
    echo ""
    echo "  File: $APK"
    echo "  Size: $SIZE"
    echo ""
else
    echo "[!] Build thất bại. Kiểm tra log bên trên."
    exit 1
fi
