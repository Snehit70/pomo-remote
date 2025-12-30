#!/bin/bash
set -e

PROJECT_DIR="$(pwd)"
SDK_DIR="$PROJECT_DIR/android-sdk"
GRADLE_DIR="$PROJECT_DIR/gradle-dist"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-9.2.1-bin.zip"

GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${GREEN}=== Pomo Remote Lightweight Builder ===${NC}"

export JAVA_HOME="/usr/lib/jvm/java-25-openjdk"

mkdir -p "$SDK_DIR/cmdline-tools"
mkdir -p "$GRADLE_DIR"

if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Downloading Android Command Line Tools..."
    wget -q --show-progress -O tools.zip "$CMDLINE_TOOLS_URL"
    unzip -q tools.zip -d "$SDK_DIR/cmdline-tools"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -f tools.zip
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

if [ ! -d "$SDK_DIR/platforms/android-34" ]; then
    echo "Installing SDK components..."
    yes | sdkmanager --licenses > /dev/null 2>&1
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
fi

if [ ! -f "$GRADLE_DIR/gradle-9.2.1/bin/gradle" ]; then
    echo "Downloading Gradle..."
    wget -q --show-progress -O gradle.zip "$GRADLE_URL"
    unzip -q gradle.zip -d "$GRADLE_DIR"
    rm -f gradle.zip
fi

export PATH="$GRADLE_DIR/gradle-9.2.1/bin:$PATH"

echo -e "${GREEN}Building APK...${NC}"
gradle assembleDebug

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo -e "${GREEN}Build Success!${NC}"
    echo "APK location: $APK_PATH"
    
    if command -v adb &> /dev/null; then
        if adb get-state 1>/dev/null 2>&1; then
            echo "Installing to connected device..."
            adb install -r "$APK_PATH"
            echo "Installed!"
        fi
    fi
else
    echo "Build failed. Check output above."
fi
