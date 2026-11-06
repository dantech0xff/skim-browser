# F-Droid readiness

This page is a draft. Nothing here has been submitted to F-Droid.

Store text is in `fastlane/metadata/android/en-US` and `fastlane/metadata/android/vi-VN`. Each locale has a title, a short description, a full description, and `changelogs/1.txt` for versionCode 1. There are no phone screenshots. `design/screens/` holds design mockups, not captures of a running device, so those files are not store screenshots. Real screenshots still have to be taken on a phone before a listing goes up.

The dependency check is `docs/foss-licenses.md`. The draft metadata file is `docs/fdroiddata.yml`.

## What already helps a rebuild

`applicationId` is `com.dantech.skimbrowser`. `versionCode` is `1` and `versionName` is `1.0`, both written in `app/build.gradle.kts`. They are not computed from the date, the git revision, or CI. The release build does not use Google Play services. `release` is signed by the builder. `releaseSmoke` uses the debug key and is not a ship variant.

A byte-for-byte rebuild has not been verified. The APK still depends on four strings that are not in git. `assembleRelease` fails until `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, and `skim.aiDataPolicyUrl` are https URLs passed on the command line or in `local.properties`. Two builds with different strings are different APKs. The proxy URL stays out of this repository. `docs/fdroiddata.yml` does not contain it.

## NonFreeNet

Skim AI sends page text to the project's proxy, and the proxy calls a hosted model. That network service is not free software. The draft metadata marks the app NonFreeNet. That anti-feature is accepted.

The feature is off until the person allows a site. `askBeforeSending` defaults to true. Incognito tabs are never sent. An empty proxy URL fails before any connection. There is one APK, and it includes Skim AI. A flavor that strips the client was considered and rejected. Asking first does not remove NonFreeNet, because the client stays in the binary.

## Decisions

These are decided.

1. One APK, with Skim AI. Accept NonFreeNet. Do not add a flavor that removes the AI client.
2. The application id stays `com.dantech.skimbrowser`.
3. Google Sans Flex stays under OFL 1.1. Material Symbols stay under Apache-2.0.

The four release URLs are still unset in git. The fdroiddata recipe cannot be submitted until those public URLs are chosen and placed in the F-Droid build recipe, not in this repository. Real phone screenshots are still missing.
