# YouTube Adblock

Experimental ad-blocking patch and non-root build tools for the exact **YouTube 21.40.161 arm64** APK set extracted from an installed Play Store app.

This repository contains the patch/build code and playback helper sources. Building still requires the original APK files. The released APK retains Google's compiled app and assets. This is an unofficial modified application.

**Static checks are separate from runtime testing. Login, playback and actual ad suppression have not been verified on a phone.** This version is outside the checked upstream compatibility list.

## Download and install

Download `YouTube-Ad-Test-21.40.161.apk` from the [experimental prerelease](https://github.com/rendoarsandi/youtube-adblock/releases/tag/v0.1.0-experimental). APKs are Release attachments, not Git files. The release includes its SHA-256 checksum and validation report. The APK is 117.7 MB.

1. Install the standard non-Huawei APK from the [official ReVanced GmsCore release](https://github.com/ReVanced/GmsCore/releases/tag/v0.3.13.2.250932), if not already installed for the Music test.
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

There is no complete component-ad filter, Shorts-ad guarantee, settings screen or startup GmsCore checker. Casting initialization is bypassed; casting and Android Auto remain untested. Client profiles and server behavior may change.

Static validation preserved all 56,152 original classes and checked 610 added helper classes (56,762 total). All 15 hooks matched. Native-library and asset bytes were compared with the merged original; APK signatures, alignment and ZIP CRCs passed. See [the validation report](evidence/validation.json).

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
