# Gym QR — Android

A standalone, **offline Android application** that displays a large rotating gym entry QR code, using the previously verified format from an existing Synergym membership's Tasker/Termux setup.

**This is an unofficial personal-use client**, not endorsed by Synergym. A locally generated code may still be rejected if the gym changes its authentication policy.

## Features

- Open the app to show a large, centered, scanner-friendly QR code.
- Refreshes automatically every **10 seconds** (and immediately upon returning to the app).
- Uses the UTC clock and native HMAC-SHA256 signing; works offline and without Google Play Services.
- Prevents the screen from sleeping; temporarily raises brightness while showing the QR.
- One-time settings for member ID, superclub ID, club ID, tag, and HMAC signing key.
- Encrypts the signing key using Android Keystore and AES-GCM; disables Android backup.
- No network, camera, analytics, or location permissions. No credentials in this repository.

## Get an APK

Visit [GitHub Actions](https://github.com/kd40000-dot/gym/actions/workflows/android.yml), open the latest successful **Build Android APK** run, and download **Gym-QR-debug-APK** from its Artifacts section. Extract the ZIP and install \`app-debug.apk\`.

The workflow compiles with Gradle 8.9, Android SDK 35, and Java 17, then runs a synthetic signing test and builds the debug APK. The debug APK is signed by the GitHub Actions runner's debug key; the key can change between runs. For long-term updates without uninstalling, configure a persistent private signing key in a follow-up release workflow.

## First run

Enter the five values from the **working Tasker/Termux** configuration:

1. Member ID
2. Superclub ID (typically \`0\`)
3. Club ID
4. Tag (16 hexadecimal characters)
5. HMAC signing key (use the **literal text**, do not convert from hex)

These are stored only on the device. No private membership details or signing keys are committed to GitHub. Keep the phone's automatic time setting on.

## Technical

The signed message is:

\`\`\`text
TAGV5202|member|superclub|club|YYYY-MM-DDTHH:mm:ss|||tagx|
\`\`\`

The app computes \`Base64(HMAC-SHA256(UTF8(key), UTF8(message)))\` and appends it to the message before QR encoding. The timestamp is UTC (second precision, **not rounded**); a new code is generated every 10 seconds. QR rendering uses the Apache-2.0-licensed ZXing core library.

## Local development

Open the repository in Android Studio, or use Gradle 8.9 and Android SDK 35:

\`\`\`sh
gradle testDebugUnitTest assembleDebug
\`\`\`

The resulting APK will be at \`app/build/outputs/apk/debug/app-debug.apk\`.

No app-specific data is required to build. A synthetic unit test checks the signing format without publishing live membership details.
