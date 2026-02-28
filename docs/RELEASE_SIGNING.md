# Release Signing Setup

## GitHub Actions Secrets Required

### Android
- `ANDROID_KEYSTORE_BASE64`: Base64-encoded release keystore
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password
- `ANDROID_KEY_ALIAS`: Key alias name
- `ANDROID_KEY_PASSWORD`: Key password
- `SIGN_ANDROID`: Set to `true` to enable signing

Generate keystore:
```bash
keytool -genkey -v -keystore deckloot-release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias deckloot
base64 -i deckloot-release.keystore | pbcopy  # macOS: copies to clipboard
```

### iOS
- `IOS_P12_BASE64`: Base64-encoded .p12 distribution certificate
- `IOS_P12_PASSWORD`: Certificate password
- `IOS_CODE_SIGN_IDENTITY`: e.g., "Apple Distribution: Your Name (TEAMID)"
- `IOS_PROVISIONING_PROFILE_BASE64`: Base64-encoded .mobileprovision
- `SIGN_IOS`: Set to `true` to enable signing

### macOS (Desktop)
- `MACOS_P12_BASE64`: Base64-encoded Developer ID certificate
- `MACOS_P12_PASSWORD`: Certificate password
- `MACOS_SIGNING_IDENTITY`: e.g., "Developer ID Application: Your Name (TEAMID)"
- `MACOS_NOTARIZATION_APPLE_ID`: Apple ID email
- `MACOS_NOTARIZATION_PASSWORD`: App-specific password
- `SIGN_MAC`: Set to `true` to enable signing

## Local Development

### Android
1. Copy `keystore.properties.template` to `keystore.properties`
2. Fill in your keystore path and credentials
3. Run `./gradlew assembleRelease`

### iOS
Open `deckLoot/deckLoot.xcodeproj` in Xcode. Automatic signing is configured with team ID `3HZPZ5X2U3`.

### Desktop
Run `./gradlew packageReleaseDistributionForCurrentOS`
