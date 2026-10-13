# YouTube Adblock

Experimental ad-blocking patch and non-root build tools for the exact **YouTube 21.40.161 arm64** APK set extracted from an installed Play Store app.

This repository contains patch/build code and optional playback helper sources. Building still requires the original APK files. The released APK retains Google's compiled app and assets. This is an unofficial modified application.

**The user confirmed v0.1.3 plays normally; its installed APK digest matches the release. Ad cards still remain below the video / suggested videos, so display-ad blocking is partial.** The recovery preserves YouTube's original playback code and adds a narrow display-ad filter. The user confirmed that v0.1.2 launches but is laggy and playback starts, then buffers/stops. Earlier releases contain the feed-register crash defect. This version is outside the checked upstream compatibility list.

## Download and install

Download `YouTube-Adblock-v0.1.3.apk` from the [experimental prerelease](https://github.com/rendoarsandi/youtube-adblock/releases/tag/v0.1.3). APKs are Release attachments, not Git files. The release includes its SHA-256 checksum and validation report. The APK is about 117 MB. `21.40.161` is the original YouTube version; `v0.1.3` is this project's patch revision. Older diagnostic/fix APKs update the same test package and are superseded.

1. Install the standard non-Huawei APK from the [official ReVanced GmsCore release](https://github.com/ReVanced/GmsCore/releases/tag/v0.3.13.2.250932), if not already installed for the Music test. **Open its microG screen once before launching this app.** Allow it to run in the background; sign-in is handled separately.
2. Install this project's APK and open **YouTube (Ad test)**.
3. Sign in yourself through GmsCore if needed; its screen may say **microG**.
4. Test opening a video, several minutes of playback, seeking, a second video, and whether playback/feed ads appear. Report the exact error and device/Android version if it fails.

The test package is `app.revanced.android.youtube`, so it can coexist with official YouTube. GmsCore also coexists with Google Play Services. The build tools do not install or launch the app.

## What changes

| Area | Implementation |
| --- | --- |
| Video ads | Returns early from the uniquely matched video-ad trigger loader. |
| Browse/search ads | Sets the browse/search client OS field to `Android Automotive`, following the upstream request-context approach. |
| Display ads | Replaces completed components only for explicit `carousel_ad`, `_ad_with`, `ads_video_with_context` and `watch_metadata_app_promo` patterns; preserves protected video/comment contexts. |
| Playback recovery | Restores the eight original playback owner classes, including request routing, stream data, POST bodies and feature decisions; excludes the older stream-fetch helpers from the default APK. |
| Non-root sign-in | Redirects selected service constants and metadata to ReVanced GmsCore. |
| Package/resources | Renames the install package and provider authorities, merges five APKs and repairs sparse resource type specifications. |

There is no complete component-ad filter, Shorts-ad guarantee or settings screen. Unknown components remain visible. A startup check detects missing/disabled GmsCore and opens installation/settings guidance. It also briefly acquires and closes GmsCore’s public service provider to start its provider process; it reads no provider values or account data and does not complete microG’s first-open setup. This does not guarantee that permissions, background restrictions or sign-in are ready. Casting initialization is bypassed; casting and Android Auto remain untested. Server acceptance of the modified app remains unknown.

Android ART reproduced the old feed VerifyErrors and accepted the corrected classes. The new actual-APK filter check constructs real component contexts and exercises 14 ad/protected/unknown cases through the component bridge and empty-component factory. It does not prove live ad coverage. The build independently compares all eight playback owner classes with the unmodified input, in addition to serialization, resource, signature, alignment and native-library/asset checks. Recovery findings are in [the playback report](docs/playback-recovery.md).

Install v0.1.2 or newer to obtain the feed-register fix. Install and open **ReVanced GmsCore / microG** once before launching this app. The earlier microG workaround did not rule out the now-confirmed bytecode defect. If the corrected build still closes, a crash report is needed to identify the remaining failure.

## Experiment and risks

Crashes, failed login, playback interruptions and incomplete ad blocking are possible. Static DEX comparison, signatures and alignment checks do not prove functionality. Upstream's checked supported version list ends at 20.40.45; these hooks were adapted to 21.40.161.

[YouTube's terms](https://www.youtube.com/static?template=terms) restrict modification and reverse engineering, subject to stated exceptions. Compliance, undetectability and freedom from account restrictions are not guaranteed. Google can restrict a service or [disable an entire Google Account](https://support.google.com/accounts/answer/40695?hl=en). A separate account does not guarantee isolation from consequences for other accounts. [YouTube's termination policy](https://support.google.com/youtube/answer/2802168?hl=en) also prohibits circumventing channel termination with another channel.

The default recovery build uses YouTube's original network/playback implementation. The optional `compat` build includes the older upstream stream-fetch helper and uses relevant headers supplied by the app; that mode is unreliable and is not the released recovery APK. No account credentials are included in the repository. Do not post passwords, tokens or account data in issues.

The app writes a small local report after about 15 seconds in `Downloads/YouTube-Adblock-Diagnostics/YouTube-v0.1.3-local-*.txt`. It records successful display replacements, filter errors, main-looper delay and this app's recent exit reason codes/timestamps. File work runs in the background and stops after 20 snapshots, roughly five minutes. It records no account/provider values, headers, URLs or video IDs and sends no report. Network outcomes are not collected, so this report cannot identify a streaming-server rejection. A pending looper ping alone does not establish a stall. Sessions shorter than 15 seconds may produce no report.

## Build from source

Requirements: Python 3.11+, JDK 21, a D8 compiler JAR, `aapt`, `zipalign` supporting `-P 16`, and `apksigner`/`keytool` for signing. Android/Termux is the checked host; other hosts are unverified. The optional older `--playback-mode compat` also requires `protoc` 35.1.

Provide your own exact installed APK files in a private directory:

```text
base.apk
split_config.arm64_v8a.apk
split_config.en.apk
split_config.in.apk
split_config.xxhdpi.apk
```

Use `adb shell pm path com.google.android.youtube` to find installed APK paths, then `adb pull` each returned file if your computer/device access permits. Installation retains APK files even when the installer download is gone. Other versions, languages and density sets are rejected by the pinned checksums in `build.py`.

```bash
python build.py --input-dir /private/path/to/apks --d8-jar /path/to/d8.jar
```

On Termux, D8 is normally at `$PREFIX/share/java/d8.jar`. The build downloads pinned ReVanced CLI and APKEditor artifacts and verifies SHA-256 before use. Native playback is the default. Compatibility mode additionally downloads protobuf, regenerates/compiles the older helpers, excludes Android/JSON compilation stubs and shades protobuf to avoid collisions with Google's classes. Source, dependency and build provenance are documented in [the notices](THIRD_PARTY_NOTICES.md).

Outputs and a generated private signing key stay under ignored `.local-build/`. Keep that key private and retain it for compatible future updates. Add `--unsigned` to omit signing, or reuse a private key:

```bash
python build.py --input-dir /private/path/to/apks --d8-jar /path/to/d8.jar \
  --keystore /private/path/signing.p12 \
  --password-file /private/path/password.txt --key-alias local-experiment
```

`src/NonRootYoutube.java` applies guarded DEX/manifest changes; `src/helpers/` contains locally authored bridges; `src/playback/` contains vendored upstream playback sources, proto definitions and local support code. `build.py` orchestrates the build and static verification; `tools/fix_resource_specs.py` repairs resource provenance. [Findings](docs/findings.md) distinguish observations, inferences and unknowns.

## License

Patch/build code is under [GPL-3.0](LICENSE). See [attribution and third-party notices](THIRD_PARTY_NOTICES.md), including the protobuf BSD license. This license does not grant rights to Google's application code, branding or assets. GmsCore is linked from its official release and is not bundled or reuploaded.

## Android verifier regression check

On the Android host, with your own private APK and D8 compiler:

```bash
python tests/verify_feed_art.py --apk /private/path/to/built.apk \
  --d8-jar /path/to/d8.jar --work-dir /private/path/to/new-probe-directory \
  --display-filter
```

The check verifies the feed/component classes and calls the actual display filter on constructed, unrendered components using Android's system resources context. It does not launch YouTube, construct player requests, access accounts or use the network. Serialization checks alone did not detect the old receiver-type bug; this check uses ART's independent acceptance boundary.
