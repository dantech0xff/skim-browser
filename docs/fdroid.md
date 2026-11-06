# F-Droid readiness

This page is a draft. Nothing here has been submitted to F-Droid.

Store text is in `fastlane/metadata/android/en-US` and `fastlane/metadata/android/vi-VN`. Each locale has a title, a short description, a full description, and `changelogs/1.txt` for versionCode 1. There are no phone screenshots. `design/screens/` holds design mockups, not captures of a running device, so those files are not store screenshots. Real screenshots still have to be taken on a phone before a listing goes up.

The dependency check is `docs/foss-licenses.md`. The draft metadata file is `docs/fdroiddata.yml`.

## What already helps a rebuild

`applicationId` is `com.dantech.skimbrowser`. `versionCode` is `1` and `versionName` is `1.0`, both written in `app/build.gradle.kts`. They are not computed from the date, the git revision, or CI. The release build does not use Google Play services. `release` is signed by the builder. `releaseSmoke` uses the debug key and is not a ship variant.

A byte-for-byte rebuild has not been verified. The APK still depends on four strings that are not in git. `assembleRelease` fails until `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, and `skim.aiDataPolicyUrl` are https URLs passed on the command line or in `local.properties`. Two builds with different strings are different APKs. The proxy URL stays out of this repository. `docs/fdroiddata.yml` does not contain it.

## NonFreeNet

Skim AI sends page text to the project's proxy, and the proxy calls a hosted model. That network service is not free software. F-Droid should mark the app NonFreeNet if the binary can make that call. The draft metadata says so in English.

The feature is off until the person allows a site. `askBeforeSending` defaults to true. Incognito tabs are never sent. An empty proxy URL fails before any connection. That is opt-in behavior. It does not remove the anti-feature, because the client is still in the binary.

## Open decisions

These are not decided in this change.

1. A second product flavor with the AI client removed would be the way to publish a build that does not need NonFreeNet. It is a second APK, a second update track, and a second set of tests. This draft keeps one APK and accepts NonFreeNet. Recommendation: keep the single APK until there is a reason to ship a browser that cannot summarize.

2. The application id stays `com.dantech.skimbrowser`. A second id would let an F-Droid build sit beside another store's build, and it would also split updates. Recommendation: one id.

3. The font and the icon set are already under OFL 1.1 and Apache-2.0. No license change is required to ship them. Recommendation: leave the licenses as they are.

4. The four release URLs are still unset in git. The fdroiddata recipe cannot be submitted until those public URLs are chosen and placed in the F-Droid build recipe, not in this repository.
