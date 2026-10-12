# Attribution and third-party notices

This project adapts public [ReVanced patch sources](https://gitlab.com/ReVanced/revanced-patches/-/tree/015fe10a23ea5b919a3fda6e7da2c176517ae75d), GPL-3.0. Vendored playback Java/proto sources are identified by original path, commit and source digest in [provenance.json](src/playback/provenance.json). The upstream license is retained in [licenses/UPSTREAM-LICENSE](licenses/UPSTREAM-LICENSE).

Related upstream implementations:

- `patches/src/main/kotlin/app/revanced/patches/youtube/ad/video`: video-ad guard and fingerprints.
- `patches/src/main/kotlin/app/revanced/patches/youtube/ad/general`: browse/search Automotive context hook.
- `patches/src/main/kotlin/app/revanced/patches/shared/misc/gms` and `youtube/misc/gms`: non-root service routing.
- `patches/src/main/kotlin/app/revanced/patches/shared/misc/spoof` and `youtube/misc/spoof`: playback compatibility hooks.
- `extensions/shared/library/src/main/java/app/revanced/extension/shared/spoof` and `extensions/shared/protobuf/src/main/proto`: vendored playback sources and proto definitions.

Local modifications include exact-version instruction-digest guards, changed hook locations/fields/registers for 21.40.161, fixed client defaults, initialization before the first intercepted request, standalone support shims, generic-only error logging, omitted settings/stats UI, a class-local streaming bridge, feed-context mutation, DEX class relocation to fit reference limits, package/resource changes and static validation. Original user-agent package strings are retained except for the narrowly guarded GmsCore prime check.

The APK includes a locally shaded protobuf-javalite 4.35.1 runtime, BSD-3-Clause. Preserve [licenses/PROTOBUF-LICENSE](licenses/PROTOBUF-LICENSE). Its pinned Maven Central artifact digest is recorded in the playback build script. Generated proto sources are rebuilt from the vendored definitions.

Build tools are official [ReVanced CLI 6.0.0](https://github.com/ReVanced/revanced-cli/releases/tag/v6.0.0), including dexlib2/baksmali, and [APKEditor 1.4.9](https://github.com/REAndroid/APKEditor/releases/tag/V1.4.9), including ARSCLib. Their binaries are downloaded privately and excluded from Git. Preserve their own licenses if redistributing them.

[ReVanced GmsCore](https://github.com/ReVanced/GmsCore), based on microG, is a separate dependency. It is linked from official releases and is not bundled.

The modified APK retains Google's compiled YouTube implementation, resources and branding, which are outside the scope of this repository's source license. No original APK, extracted app asset or complete decompiled Google class is committed to Git. Release attachments contain the experimental modified APK separately.
