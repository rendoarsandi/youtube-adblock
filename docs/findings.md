# Findings for the exact installed YouTube APK set

## Observed identity and scope

YouTube 21.40.161, version code 1561301413, arm64, minimum SDK 32. Five installed APKs were copied from package-manager paths on the user's non-rooted device. Input hashes are pinned in `build.py`; the base SHA-256 is `17b08703e85da7178b21e66628dd3063079260fe6c43afd7111089346259d3e2`.

REA's portable artifact inspection returned Evidence `ev_ddd242648300db4ffa6b763f13c197d22ab5d8698050e6d1b37dd05a6bd1208f`. Android JADX analysis was unavailable on this Android/arm64 host. Further method/reference/instruction results come from local DEX parsing, dexlib2 and baksmali, not a REA execution observation.

The original set contains eight DEX files and 56,152 classes. The focused local reader decoded 275,618 code-bearing methods with zero unresolved instruction streams. The architecture includes Android components, Cronet transport, protobuf request/response models, native player libraries and Google's original resources. The patch targets selected Java/DEX behavior; native library bytes are preserved.

## Hook observations and implementation choices

- `Lacpe;->t(Ljava/util/List;)V` uniquely contains the two checked upstream ad-trigger strings. The patched body returns immediately, corresponding to the upstream ads-disabled early-return path. Actual ad suppression is inferred, not observed.
- Browse `Lalap;->b()V` and search `Lalic;->b()V` are independently identified. Their return paths call a helper that copies the request context/client info and changes only these requests' OS name to `Android Automotive`. Exact opcode/branch/frame checks and public field/API checks are recorded in `evidence/`.
- GmsCore availability, service, prime and Cast checks are narrowly matched. Package permissions, authorities and metadata use the separately installed ReVanced GmsCore dependency. This does not establish successful login.
- Eight playback hooks adapt request interception, streaming-data replacement, POST-body handling, flags and SABR behavior. Full method signatures, physical registers, original offsets and instruction SHA-256 guards are in `evidence/playback-bridge-hooks.json`.
- On this version, `/get_watch` interception uses the actual generic Cronet URL. This differs from upstream's older URI-building hook; coverage of all relevant routes remains unknown.
- Streaming helper clients initialize when the first intercepted request loads the helper class. Fixed upstream default client order: Android Reel, Android VR 1.43, visionOS, Android Creator. No settings screen is included.
- Original classes2 has only three spare method references; classes4/5/6 have none. Patched `Lakul;` and `Lalic;` move to the appended DEX with their descriptors unchanged. No class is removed from the total inventory.
- Protobuf runtime packages are shaded and compile stubs are excluded. Original Google user-agent package literals are preserved except in the matched non-root prime check.

## Verification boundary

The build checks exact input digests, unique hooks, full streaming instruction digests, helper descriptor collisions and assembly comparison for every class. Comparison normalizes unordered annotations and equivalent default static values; array order remains significant. It also checks manifest identity/GmsCore metadata, stale split/profile removal, sparse resource counts/flags, unchanged native-library/assets, ZIP CRCs, 16 KiB native alignment and APK signatures. Final metrics are in `evidence/validation.json`.

No APK installation, login, live player API request or playback occurred during this analysis. The checked upstream version list ends at 20.40.45. Functionality on 21.40.161, server acceptance, ad suppression, Shorts, live/private/age-restricted videos, seeking, transitions, background behavior and account enforcement are unresolved. Full upstream component-ad filtering is omitted; this is not a claim of complete ad coverage.

## Startup dependency follow-up

The user reported an immediate launch failure on Android 16, then confirmed that opening ReVanced GmsCore's microG screen once allowed YouTube to open. GmsCore was installed and enabled in the current profile; its installed presence alone did not establish readiness. No exception stack was available, so the exact internal cause is not known. Launch after that setup step is a user observation; playback and ad suppression remain unverified.

A local Android `app_process` probe loaded the root/MainActivity and every patched target class, applied the app theme and resolved build-data resources. Playback defaults/core and the feed helper also initialized. These are class/resource probes, not complete app execution. Independent final-DEX audits found no demonstrated feed/streaming verifier or access defect.

The startup check is injected at CU0 of `Lcom/google/android/apps/youtube/app/watchwhile/MainActivity;->onCreate(Landroid/os/Bundle;)V`, guarded by original flags20,registers31 and instruction digest `7c6d643ebfcde414937ce88c6e16301e08ee1a209dc63a79a3a7a1d55c53528a`. Receiver p0 is physical v29. Missing/disabled dependencies receive a toast and official install/settings guidance before an upstream-style graceful exit. Installed GmsCore's public provider is acquired and closed to start it, without querying values/accounts. Provider failure produces guidance; it is not treated as proof that sign-in is unavailable. Update checks and battery permission dialogs are omitted.
