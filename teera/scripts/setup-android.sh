#!/usr/bin/env bash
# Installs the Android SDK that building the app needs.
#
# A fresh cloud session starts on a clean machine, so this runs again each
# time. It is safe to re-run: the SDK manager skips what is already there.
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Installing Android command line tools into $SDK_DIR"
    mkdir -p "$SDK_DIR/cmdline-tools"
    tmp="$(mktemp -d)"
    curl -sS -o "$tmp/cmdline.zip" "$TOOLS_URL" --max-time 300
    unzip -q -o "$tmp/cmdline.zip" -d "$SDK_DIR/cmdline-tools"
    [ -d "$SDK_DIR/cmdline-tools/cmdline-tools" ] && \
        mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null

echo "sdk.dir=$SDK_DIR" > "$(dirname "$0")/../local.properties"
echo "Android SDK ready at $SDK_DIR"
