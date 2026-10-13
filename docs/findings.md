# Findings for the exact installed YouTube APK set

## Observed identity and scope

YouTube 21.40.161, version code 1561301413, arm64, minimum SDK 32. Five installed APKs were copied from package-manager paths on the user's non-rooted device. Input hashes are pinned in `build.py`; the base SHA-256 is `17b08703e85da7178b21e66628dd3063079260fe6c43afd7111089346259d3e2`.

REA's portable artifact inspection returned Evidence `ev_ddd242648300db4ffa6b763f13c197d22ab5d8698050e6d1b37dd05a6bd1208f`. Android JADX analysis was unavailable on this Android/arm64 host. Further method/reference/instruction results come from local DEX parsing, dexlib2 and baksmali, not a REA execution observation.

The original set contains eight DEX files and 56,152 classes. The focused local reader decoded 275,618 code-bearing methods with zero unresolved instruction streams. The architecture includes Android components, Cronet transport, protobuf request/response models, native player libraries and Google's original resources. The patch targets selected Java/DEX behavior; native library bytes are preserved.

## Hook observations and implementation choices

- `Lacpe;->t(Ljava/util/List;)V` uniquely contains the two checked upstream ad-trigger strings. The patched body returns immediately, corresponding to the upstream ads-disabled early-return path. Actual ad suppression is inferred, not observed.
- Browse `Lalap;->b()V` and search `Lalic;->b()V` are independently identified. Their return paths call a helper that copies the request context/client info and changes only these requests' OS name to `Android Automotive`. The source guards exact opcodes, branches, frames and public field/API use.
- GmsCore availability, service, prime and Cast checks are narrowly matched. Package permissions, authorities and metadata use the separately installed ReVanced GmsCore dependency. This does not establish successful login.
- Native mode, the default since v0.1.3, omits the eight older playback hooks and their stream-fetch helpers. The optional `compat` mode retains their request interception, replacement and feature decisions; it remains unreliable. Exact original method guards are in `src/NonRootYoutube.java`.
- Full method-reference pools require relocating modified classes to the appended DEX with their descriptors unchanged. No original class is removed from the total inventory. Build metrics list the exact relocated classes for each mode.
- Compilation stubs are excluded. Only optional compatibility mode packages the shaded protobuf runtime. Original Google user-agent package literals are preserved except in the matched non-root prime check.

## Verification boundary

The build checks exact input digests, unique hooks, full streaming instruction digests, helper descriptor collisions and assembly comparison for every class. Comparison normalizes unordered annotations and equivalent default static values; array order remains significant. It also checks manifest identity/GmsCore metadata, stale split/profile removal, sparse resource counts/flags, unchanged native-library/assets, ZIP CRCs, 16 KiB native alignment and APK signatures. Build metrics stay in the ignored build-output directory.

The initial build was validated without launching the app. The user later installed a diagnostic APK and produced the Java crash summarized below. The user confirmed normal playback in v0.1.3; login and live player API responses have not been independently captured. The checked upstream version list ends at 20.40.45. Functionality on 21.40.161, server acceptance, ad suppression, Shorts, live/private/age-restricted videos, seeking, transitions, background behavior and account enforcement are unresolved. Full upstream component-ad filtering is omitted; this is not a claim of complete ad coverage.

## Startup dependency follow-up

GmsCore was installed and enabled in the current profile. An early user report of launch after opening microG did not establish that the bytecode was sound. The later diagnostic exception identifies the feed-register defect below. Setup instructions still require installing and opening microG; provider acquisition starts its default process and does not reproduce opening its settings UI.

Earlier local probes loaded class metadata and resources without forcing feed-class initialization. Those probes were insufficient to validate register types; the ART regression below supplies that missing evidence.

The startup check is injected at CU0 of `Lcom/google/android/apps/youtube/app/watchwhile/MainActivity;->onCreate(Landroid/os/Bundle;)V`, guarded by original flags20,registers31 and instruction digest `7c6d643ebfcde414937ce88c6e16301e08ee1a209dc63a79a3a7a1d55c53528a`. Receiver p0 is physical v29. Missing/disabled dependencies receive a toast and official install/settings guidance before an upstream-style graceful exit. Installed GmsCore's public provider is acquired and closed to start it, without querying values/accounts. Provider failure produces guidance; it is not treated as proof that sign-in is unavailable. Update checks and battery permission dialogs are omitted.

## Confirmed feed-register crash and correction

The installed diagnostic APK saved a Java `VerifyError`: `alap.b()` passed v1 containing `String[]` to `FeedAds.patch(Lakvx;)`. The browse method overwrites its original receiver slot while constructing the string-array argument for `F()`. Search likewise reuses its receiver slot; its branch merges array/object types before the return hook.

The original assembly comparisons checked serialization, not ART register typing. Earlier class-load probes used `Class.forName(..., false, ...)` and did not force this verification. An Android probe with class initialization enabled independently reproduced the same failure in both classes. Earlier static reviews incorrectly assumed the receiver slot remained intact. Opening microG does not repair this bytecode defect.

The correction increases browse's register frame 2→3 and search's 3→4. The input receiver moves to the new highest register (v2/v3); an entry `move-object` initializes the old physical slot (v1/v2), preserving every original instruction operand. The original body may reuse that old slot while the return hook receives the preserved request object. Validation order and search branch coverage remain unchanged. Full original instruction digests guard both adapted methods.

`tests/verify_feed_art.py` initializes the two actual endpoint classes under the device's ART runtime, using a private APK and no application launch. It exits1 for the broken APK's VerifyErrors; the corrected APK must pass both endpoints. This independent platform regression does not construct requests, access accounts or prove ad blocking. Raw private crash reports are not committed.

The user confirmed that the uniquely named corrected APK updates successfully and YouTube opens. The installed APK SHA-256 was independently read back and matches `ed4e0af147eeb8a53f1ad77f9507a096f16a6cb5a2f8bb00e92e5e4fe3cafe5e`. A preceding “still closes” retry still had the old diagnostic APK installed, as confirmed by its different digest and unchanged VerifyError. Application launch is user-confirmed. The user subsequently reported lag and playback starting then buffering/stopping, plus a remaining below-player ad. See [the native recovery investigation](playback-recovery.md) for the later changes and evidence limits.

## Native recovery follow-up

v0.1.3 defaults to native playback and omits all eight older compatibility hooks. Its eight playback owner classes are independently compared with original input classes. The narrow display filter runs after completed component construction/cleanup; actual-APK ART checks exercise its real factory across 14 cases. This does not prove live ad coverage or full playback. Current source, validation and remaining gaps are recorded in [playback-recovery.md](playback-recovery.md).

## Targeted companion and Shorts filtering

v0.1.4 collapses only two dedicated ad stubs in the mixed companion layout, plus the legacy ad-card root. Android ViewStub transfers its own layout parameters to the inflated view, so changing only the legacy root is insufficient. The generic Elements resource and other companion stubs remain unchanged. Examined dedicated ad owners can restore visibility but do not restore height. Live below-player coverage remains to be tested.

Separate sponsored Shorts are native reel entries. The filter combines native video and image ad classification, removes entries before dynamic insertion and paired feed assembly, and filters both seed constructors after original validation. Command and tracking lists retain matching indices and ordinary-item order. Receiver registers are preserved when constructor frames grow; final fields are assigned inside their owning constructors. All original playback-owner classes remain unchanged. Real native protobuf fixtures and ART tests check seed behavior and register verification, without app launch, account access or network use.

Signed v0.1.4 passed ART feed/component/Shorts initialization, the 14-case display bridge check, real native video/image seed filtering with paired tracking and immutable inputs, and actual Android companion-layout parsing. The same seed assertion fails on v0.1.3 (four retained entries versus two); its companion layout also fails the zero-height contract. The new layout snapshots preserve every non-target node/attribute. Native mode preserves 56,152 original classes, adds 11 helper classes, applies 14 method hooks and changes only two packaged XML members. The signer matches previous builds. APK SHA-256: `79b600a82b6efca3997bafff48c905e2e3d6314de9de37c9786f9147567d09bc`. These checks do not observe live ads, authenticated requests or app playback.
