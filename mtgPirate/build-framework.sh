#!/bin/bash

set -e

# Build script for Xcode to automatically build the Kotlin framework
# This should be added as a "Run Script" build phase in Xcode

cd "$SRCROOT/.."

echo "Building Kotlin framework for iOS..."

# Detect which architecture to build based on the current SDK
if [[ "$PLATFORM_NAME" == "iphonesimulator" ]]; then
    if [[ "$ARCHS" == *"arm64"* ]] || [[ "$(uname -m)" == "arm64" ]]; then
        echo "Building for iOS Simulator (Apple Silicon)"
        ./gradlew :linkDebugFrameworkIosSimulatorArm64
        FRAMEWORK_PATH="build/bin/iosSimulatorArm64/debugFramework"
    else
        echo "Building for iOS Simulator (Intel)"
        ./gradlew :linkDebugFrameworkIosX64
        FRAMEWORK_PATH="build/bin/iosX64/debugFramework"
    fi
else
    echo "Building for iOS Device"
    ./gradlew :linkDebugFrameworkIosArm64
    FRAMEWORK_PATH="build/bin/iosArm64/debugFramework"
fi

echo "✅ Kotlin framework built successfully at $FRAMEWORK_PATH"

