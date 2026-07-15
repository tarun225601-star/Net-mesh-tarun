# GitHub Actions Setup Guide — NetMesh Android

## What gets built automatically

Every time you push code to GitHub, the workflow:
1. Builds a **debug APK** (always, no setup needed) — downloadable immediately
2. Builds a **signed release APK** (when signing secrets are configured)

---

## Step 1 — Generate a signing keystore (one-time)

You need a keystore file to sign release APKs. Run this on your computer
(Java/keytool must be installed — it comes with Android Studio's JDK):

```bash
keytool -genkeypair \
  -v \
  -keystore netmesh-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias netmesh-key
```

It will ask for:
- Keystore password  → choose a strong password, save it somewhere safe
- Key password       → can be the same as keystore password
- Name, org, etc.   → fill in anything (or press Enter to skip)

This creates `netmesh-release.jks`. **Keep this file safe — if you lose it you
cannot update your app on the Play Store.**

---

## Step 2 — Encode the keystore as base64

```bash
# macOS
base64 -i netmesh-release.jks | pbcopy     # copies to clipboard

# Linux
base64 -w 0 netmesh-release.jks            # prints to terminal — copy the output

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("netmesh-release.jks")) | clip
```

---

## Step 3 — Add secrets to GitHub

1. Open your GitHub repository in a browser
2. Click **Settings** (top tab)
3. Left sidebar → **Secrets and variables** → **Actions**
4. Click **New repository secret** for each of these four secrets:

| Secret name          | Value                                         |
|----------------------|-----------------------------------------------|
| `KEYSTORE_BASE64`    | The base64 string you copied in Step 2        |
| `KEYSTORE_PASSWORD`  | The keystore password you chose               |
| `KEY_ALIAS`          | `netmesh-key` (or whatever alias you used)    |
| `KEY_PASSWORD`       | The key password you chose                    |

---

## Step 4 — Download your APK after a build

1. Go to your GitHub repository
2. Click the **Actions** tab
3. Click the most recent **Build NetMesh Android APK** run
4. Scroll to the bottom → **Artifacts** section
5. Click **NetMesh-release-signed-N** or **NetMesh-debug-N** to download the zip
6. Unzip it — the APK is inside

---

## Installing the APK on your phone

**Via USB (ADB):**
```bash
adb install app-release.apk
```

**Directly on phone:**
1. Transfer the APK to your phone (email, Google Drive, USB cable)
2. On your phone: Settings → Apps → Special app access → Install unknown apps
3. Allow your file manager or browser to install APKs
4. Tap the APK file to install
