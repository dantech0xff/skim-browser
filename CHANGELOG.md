# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

A tag `vX.Y.Z` publishes the matching `## [X.Y.Z]` section as a GitHub Release. The workflow is `.github/workflows/release.yml`. It uses the workflow token only. It does not sign an APK, upload an artifact, or read a repository secret. A GitHub Release is not permission to ship. That rule is `docs/deployment-guide.md`.

Move notes from `[Unreleased]` into a dated version section before pushing the tag.

## [Unreleased]

### Added

- Pages drop requests whose hosts are on the pinned EasyList and EasyPrivacy snapshot. A site can turn that off. The choice for an incognito tab lasts only for the session. Leftover ad frames and empty slots on a blocked site are hidden with the cosmetic rules from that snapshot.
- Public `http` pages are opened over `https` first. When that cannot connect, the page offers the HTTP version. The first incognito tab of a session asks the device to authenticate when the device can.
- An article can be saved on the device and opened later without a network, with a one-shot summary.
- An answer can be translated to Vietnamese and read aloud. A page summary is three short points.
- A horizontal swipe on the browsing bar moves to the previous or next tab of the same kind, through tab groups and then Other. Whole-screen changes fade as they slide.

### Changed

- The app talks to the model through its own OkHttp client. The Anthropic Java SDK is not in the APK.
- Google Sans Flex ships with the weight axis only.
