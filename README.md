# Freaklog Android
A fork from https://github.com/isaakhanimann/psychonautwiki-journal-android/
## Building
```
nix develop .
```
```
nix run github:tadfisher/gradle2nix/v2 -- assembleRelease
```
The unsigned apk should be under `app/build/outputs/apk/release/app-release-unsigned.apk`
