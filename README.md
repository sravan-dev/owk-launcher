# Owk Launcher

A minimal, ad-free home screen for Android TV. Shows your TV apps as a banner grid over a wallpaper, with a clock and settings button. No suggestions, no sponsored rows, no background services.

Plain Java, no Gradle, no libraries. Cold start is about 1 second.

## Features

- Grid of every TV (Leanback) app, using each app's banner
- 12-hour clock, settings gear
- Hold **OK** (or press **Menu**) on an app: move left/right, hide, app info
- Gear menu: Android settings, change wallpaper, default wallpaper, show hidden apps
- **Home** while on the home screen jumps back to the first app

## Build

Requires the Android SDK (build-tools 36.0.0, platform android-35) and JDK 17. Paths are set at the top of `build.ps1`.

```powershell
# Put a 1920x1080 image at wallpaper.jpg (the bundled default wallpaper), then:
.\build.ps1
```

Produces `OwkLauncher.apk`. The first build creates a signing key `owk.jks`; keep it so later builds install as updates.

## Install and set as default

```sh
adb install -r OwkLauncher.apk
adb shell cmd role add-role-holder android.app.role.HOME com.owk.launcher
```

To stop the stock Google TV home from showing suggestions or reclaiming the Home button:

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Undo with `adb shell pm enable com.google.android.tvlauncher`.
