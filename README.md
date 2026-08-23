# ScrollCheck Android — Real Usage Tracker

This is the first Android implementation of ScrollCheck.

## What is real
- Reads Android app usage statistics after the user explicitly grants Usage Access.
- Tracks YouTube (`com.google.android.youtube`) and Instagram (`com.instagram.android`) usage.
- Shows today's minutes for each app.
- Calculates a simple first-version Scroll Balance score.
- Works locally without a backend.

## Important limitation
Android usage statistics tell us app foreground usage. They do not automatically reveal the meaning/category of each individual video. The app therefore does not pretend that content classification is already real.

## Build
Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on an Android phone/emulator.

On first run:
1. Open ScrollCheck.
2. Tap **Grant Usage Access**.
3. Enable ScrollCheck in Android's Usage Access settings.
4. Return to ScrollCheck.
5. Use YouTube/Instagram normally.
6. Return to ScrollCheck and tap **Refresh data**.

## Next development
- Local Room database for daily history
- Real weekly charts
- Goal/streak/reward engine
- Late-night detection from usage event timestamps
- App-specific sessions
- Optional user-entered/estimated content classification
- Polished Compose version matching the existing web prototype
- Privacy controls and export/delete data


## Cloud build from a phone

The project includes `.github/workflows/build-apk.yml`.

GitHub Actions can run the build on a GitHub-hosted Linux runner and save the generated APK as a downloadable workflow artifact. GitHub documents that workflows can build/test code on hosted runners and that artifacts can store binary files after a workflow completes.

Phone steps:
1. Create/sign in to a GitHub account.
2. Create a new repository, for example `ScrollCheck`.
3. Upload the contents of this project ZIP to the repository (not the ZIP file itself).
4. Make sure `.github/workflows/build-apk.yml` is present.
5. Open the repository's **Actions** tab.
6. Select **Build ScrollCheck APK**.
7. Tap **Run workflow**.
8. When it finishes successfully, open the workflow run and download the **ScrollCheck-debug-apk** artifact.
9. Extract the downloaded artifact and install `app-debug.apk` on your Android phone.
10. On first launch, grant ScrollCheck Usage Access in Android Settings.

The cloud build creates a debug APK for testing. It is not a Play Store release build and is not digitally signed with a personal release key.
