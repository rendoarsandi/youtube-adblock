# YouTube Adblock

Experimental ad-blocking patch and non-root build tools for the exact **YouTube 21.40.161 arm64** APK set extracted from an installed Play Store app.

This repository contains the patch/build code and playback helper sources. Building still requires the original APK files. The released APK retains Google's compiled app and assets. This is an unofficial modified application.

**A launch crash was traced to a feed-hook receiver register and corrected in v0.1.2. Earlier releases contain this defect. Full launch, playback and ad suppression still require device testing.** This version is outside the checked upstream compatibility list.

## Download and install

Download `YouTube-Ad-Test-21.40.161.apk` from the [experimental prerelease](https://github.com/rendoarsandi/youtube-adblock/releases/tag/v0.1.2-feed-fix). APKs are Release attachments, not Git files. The release includes its SHA-256 checksum and validation report. The APK is 117.7 MB.

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
| Playback compatibility | Ports upstream request fallback, stream fetch/cache/client logic, streaming-data replacement, POST-body adjustment and selected playback flags; disables SABR. |
| Non-root sign-in | Redirects selected service constants and metadata to ReVanced GmsCore. |
| Package/resources | Renames the install package and provider authorities, merges five APKs and repairs sparse resource type specifications. |

There is no complete component-ad filter, Shorts-ad guarantee or settings screen. A startup check now detects missing/disabled GmsCore and opens installation/settings guidance. It also briefly acquires and closes GmsCore’s public service provider to start its provider process; it reads no provider values or account data and does not complete microG’s first-open setup. This does not guarantee that permissions, background restrictions or sign-in are ready. Casting initialization is bypassed; casting and Android Auto remain untested. Client profiles and server behavior may change.

The regression check uses Android ART to verify both feed classes; it reproduced the old APK’s VerifyErrors and passed both corrected classes. See [the crash-fix evidence](evidence/feed-crash-fix.json). Static validation preserved all 56,152 original classes and checked 611 added helper classes (56,763 total). All 16 hooks matched. Native-library and asset bytes were compared with the merged original; APK signatures, alignment and ZIP CRCs passed. See [the validation report](evidence/validation.json).

Install v0.1.2 or newer to obtain the feed-register fix. Install and open **ReVanced GmsCore / microG** once before launching this app. The earlier microG workaround did not rule out the now-confirmed bytecode defect. If the corrected build still closes, a crash report is needed to identify the remaining failure.

## Experiment and risks

Crashes, failed login, playback interruptions and incomplete ad blocking are possible. Static DEX comparison, signatures and alignment checks do not prove functionality. Upstream's checked supported version list ends at 20.40.45; these hooks were adapted to 21.40.161.

[YouTube's terms](https://www.youtube.com/static?template=terms) restrict modification and reverse engineering, subject to stated exceptions. Compliance, undetectability and freedom from account restrictions are not guaranteed. Google can restrict a service or [disable an entire Google Account](https://support.google.com/accounts/answer/40695?hl=en). A separate account does not guarantee isolation from consequences for other accounts. [YouTube's termination policy](https://support.google.com/youtube/answer/2802168?hl=en) also prohibits circumventing channel termination with another channel.

The playback helper makes requests to Google's YouTube API when the app runs and uses relevant headers supplied by the app, following upstream behavior. Debug logging is disabled; local support shims print generic errors without credentials, headers or request URLs. No account credentials are included in the repository. Do not post passwords, tokens or account data in issues.

## Build from source

Requirements: Python 3.11+, JDK 21, `protoc` 35.1, a D8 compiler JAR, `aapt`, `zipalign` supporting `-P 16`, and `apksigner`/`keytool` for signing. Android/Termux is the checked host; other hosts are unverified.

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

On Termux, D8 is normally at `$PREFIX/share/java/d8.jar`. The build downloads pinned ReVanced CLI, APKEditor and protobuf runtime artifacts and verifies SHA-256 before use. It regenerates protobuf classes, compiles the helpers, excludes Android/JSON compilation stubs and shades protobuf to avoid collisions with Google's classes. Source, dependency and build provenance are documented in [the notices](THIRD_PARTY_NOTICES.md).

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
  --d8-jar /path/to/d8.jar --work-dir /private/path/to/new-probe-directory
```

The check initializes only the actual browse/search endpoint classes, without launching YouTube. Serialization checks alone did not detect the receiver-type bug; this test checks ART's independent acceptance boundary.
