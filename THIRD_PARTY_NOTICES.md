# Attribution and third-party notices

The v0.1.7 local update corrects the shared `GmsCoreSupport` status for this project's existing non-root integration, allowing the maintained streaming patch's default to activate. It adds locally authored, bounded diagnostic capture for numeric playback outcomes and exception types/stacks; it does not collect request URLs, headers, bodies or account/video identifiers.

The local `build_stream_repair.py` route uses [anddea's v4.4.0-dev.3 patch bundle](https://github.com/anddea/revanced-patches/releases/tag/v4.4.0-dev.3) and [Morphe Desktop 1.19.1](https://github.com/MorpheApp/morphe-desktop/releases/tag/v1.19.1). They include work by anddea, inotia00, Morphe and ReVanced under GPLv3 and their preserved additional attribution terms. The resulting application retains upstream settings and visible credits.

Local modifications for YouTube 21.40.161: an exact guarded player-state fingerprint fallback and null-argument guard, and a content-provider diagnostic location that accepts either ordering while preserving the map-insertion match. `tools/stream_compat/` contains the adaptation sources and original artifact/class/method digests. The upstream patch bundle is modified locally by this project; this build is not an upstream release. All unrelated bundle entries are compared byte for byte. Packaging restores the mod's package name, changes its plain label and update code, and preserves original sparse resource flags alongside added entries.

This project adapts public [ReVanced patch sources](https://gitlab.com/ReVanced/revanced-patches/-/tree/015fe10a23ea5b919a3fda6e7da2c176517ae75d), GPL-3.0. Vendored playback Java/proto sources are identified by original path, commit and source digest in [provenance.json](src/playback/provenance.json). The upstream license is retained in [licenses/UPSTREAM-LICENSE](licenses/UPSTREAM-LICENSE).

Related upstream implementations:

- `patches/src/main/kotlin/app/revanced/patches/youtube/ad/video`: video-ad guard and fingerprints.
- `patches/src/main/kotlin/app/revanced/patches/youtube/ad/general`: browse/search Automotive context hook.
- `patches/src/main/kotlin/app/revanced/patches/shared/misc/gms` and `youtube/misc/gms`: non-root service routing.
- `patches/src/main/kotlin/app/revanced/patches/shared/misc/spoof` and `youtube/misc/spoof`: playback compatibility hooks.
- [YouTube background playback patch](https://gitlab.com/ReVanced/revanced-patches/-/tree/main/patches/src/main/kotlin/app/revanced/patches/youtube/misc/backgroundplayback): eligibility, settings and kids-policy changes adapted in `BackgroundPlayback.java` on 2026-10-22, with exact-version guards for 21.40.161.
- `extensions/shared/library/src/main/java/app/revanced/extension/shared/spoof` and `extensions/shared/protobuf/src/main/proto`: vendored playback sources and proto definitions.

Local modifications include exact-version instruction-digest guards, changed hook locations/fields/registers for 21.40.161, fixed client defaults, initialization before the first intercepted request, standalone support shims, generic-only error logging, omitted settings/stats UI, a class-local streaming bridge, feed-context mutation, DEX class relocation to fit reference limits, package/resource changes and static validation. Original user-agent package strings are retained except for the narrowly guarded GmsCore prime check.

The optional compatibility APK includes a locally shaded protobuf-javalite 4.35.1 runtime, BSD-3-Clause. Preserve [licenses/PROTOBUF-LICENSE](licenses/PROTOBUF-LICENSE). Its pinned Maven Central artifact digest is recorded in the playback build script. Generated proto sources are rebuilt from the vendored definitions. The default native recovery APK excludes those playback/protobuf helpers.

Build tools are official [ReVanced CLI 6.0.0](https://github.com/ReVanced/revanced-cli/releases/tag/v6.0.0), including dexlib2/baksmali, and [APKEditor 1.4.9](https://github.com/REAndroid/APKEditor/releases/tag/V1.4.9), including ARSCLib. Their binaries are downloaded privately and excluded from Git. Preserve their own licenses if redistributing them.

[ReVanced GmsCore](https://github.com/ReVanced/GmsCore), based on microG, is a separate dependency. It is linked from official releases and is not bundled.

The modified APK retains Google's compiled YouTube implementation, resources and branding, which are outside the scope of this repository's source license. No original APK, extracted app asset or complete decompiled Google class is committed to Git. Release attachments contain the experimental modified APK separately.

The local `GmsStartup` helper follows upstream GmsCore installation checking, public-provider acquisition and guidance followed by graceful exit. It uses Java reflection to avoid packaging framework stubs, omits account queries, update-network requests and battery permission dialogs. Source reference: [GmsCoreSupport.java](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/extensions/shared/library/src/main/java/app/revanced/extension/shared/GmsCoreSupport.java).

The narrow `DisplayAdPolicy` patterns and protected-path exceptions adapt [AdsFilter.java](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/extensions/youtube/src/main/java/app/revanced/extension/youtube/patches/litho/AdsFilter.java), GPL-3.0. The exact-version display bridge and bounded local diagnostic writer are locally authored. Stock component construction/cleanup finishes before suppression; the policy searches the existing StringBuilder without copying its path. No full decompiled Google class is published.
