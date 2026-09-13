#!/usr/bin/env bash
# Installs the Android command line tools + SDK inside the Codespace.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-/usr/local/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

echo "==> Android SDK dir: $SDK"
sudo mkdir -p "$SDK"
sudo chown -R "$(id -u)":"$(id -g)" "$SDK"
mkdir -p "$SDK/cmdline-tools"

if [ ! -d "$SDK/cmdline-tools/latest" ]; then
	echo "==> Downloading command line tools"
	cd /tmp
	curl -fsSLO "https://dl.google.com/android/repository/$TOOLS_ZIP"
	unzip -q -o "$TOOLS_ZIP"
	rm -rf "$SDK/cmdline-tools/latest"
	mv cmdline-tools "$SDK/cmdline-tools/latest"
	rm -f "$TOOLS_ZIP"
fi

export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

echo "==> Accepting licenses"
yes | sdkmanager --sdk_root="$SDK" --licenses > /dev/null || true

echo "==> Installing platform + build tools"
sdkmanager --sdk_root="$SDK" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "sdk.dir=$SDK" > "$ROOT/local.properties"

if [ ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
	echo "==> Generating the Gradle wrapper"
	cd "$ROOT"
	gradle wrapper --gradle-version 8.7 || echo "wrapper generation skipped (use 'gradle assembleDebug' instead)"
fi

echo "==> Done. Build with:  ./gradlew assembleDebug"
