# Playback recovery and narrow display filter

## Observed failure

The user confirmed v0.1.2 launches, then reported lag and a video that starts but buffers/stops. The installed launch-confirmed APK digest is recorded in the feed-fix evidence. An ad remains below the player. That display placement does not establish whether in-video ads were blocked.

Focused inspection of the exact 21.40.161 methods found three problems in applying the older compatibility approach:

- The replacement stream helper can wait up to 20 seconds on the constructor's calling thread. Its debug-only warning does not prevent a main-thread wait. Five constructor call sites do not establish that the future has already completed.
- The generic URL hook redirects `get_watch` although its fetch helper ignores that endpoint. This version multiplexes subordinate requests in its protobuf body; reliable older-style fallback has not been established.
- POST-body removal applies to every matching `videoplayback` POST even when replacement fetching returned null. Native fallback therefore retains compatibility changes intended for replacement streams.

These are observed code properties, not a captured runtime cause of this user's buffering. A separate anonymous public-video probe from Termux received HTTP200 with LOGIN_REQUIRED for Android VR1.43 and UNPLAYABLE for visionOS, both without stream formats. It used no app/account/visitor headers and does not establish authenticated-client behavior.

Current upstream work also discusses rejecting unusable SABR-only responses, but its effectiveness for this device/version has not been established. Sources: [ReVanced MR6983](https://gitlab.com/ReVanced/revanced-patches/-/merge_requests/6983) and [the upstream playback investigation](https://github.com/revanced/revanced-manager/issues/3534). The latter corrected earlier claims on October11; it is evidence of a reported investigation, not proof that its experimental remedies solve this case.

## Recovery choice

Native mode removes all eight older playback hooks together: URL filtering/fetch, initplayback redirection, constructor stream replacement, POST deletion and four feature/Platypus decisions. It excludes the old helper and shaded protobuf runtime from the default APK. The builder independently compares the eight serialized playback owner classes against their original input classes. They must match in full, including the constructor without the replacement bridge.

GmsCore routing, the dependency/startup correction, feed-context hooks and video-ad guard remain. The modified signature can still affect server acceptance. Restoring original playback is an isolation/recovery choice, not a claim that tokens, playback, seeking, live videos or accounts work.

Compatibility mode remains available through `--playback-mode compat` for source experiments. It retains the earlier behavior and is not the v0.1.3 recovery release.

## Display-filter boundary

The below-player companion factory is identifiable from its `belowPlayerAdLayoutRenderer` diagnostic, but returning null there without validating callers is unjustified. The narrow filter instead runs after stock construction and cleanup in:

`Lvin;->a(Lhgt;Lwpp;Lvyj;Ljava/util/List;Lwqk;Z)Lhgo;`

Its original instruction digest is `dfa4001460c2473ac1221776e2c2d862823e718687f814e26479101447ae81af`; the sole return is at code unit255 after cleanup253. Original context registers v23/v24 survive the full method; dead locals v1/v2 pass them to the bridge. `Lwpp.o` is the identifier and `Lwpp.m` is the path StringBuilder. The public `Lhqn.aE(Lhgt):Lhqm` factory and public `Lhqm.a:Lhqn` return an empty component compatible with `Lhgo`.

Only a narrow subset of explicit upstream ad patterns is used. Exceptions protect normal home/related videos, comments/replies and the recent library shelf. Unknown/null contexts remain visible. The helper searches the existing path builder, preserves the original component on an exception and records only successful replacement/error counts. It does not log component paths or identifiers.

The actual-APK ART probe constructs real component contexts and exercises the helper/factory for 14 positive and protected/unknown cases. It does not render a screen or observe this particular live ad. Native component names may be unavailable; the larger upstream protobuf/native-filter pipeline is not ported. Coverage remains partial.

## Local evidence limits

The bounded local report contains display counts, main-looper delay and this app's exit reason codes/timestamps. It excludes accounts, provider values, headers, URLs and video IDs and makes no network requests. It does not record CDN response codes or prove why a video stopped. The user confirmed normal playback in the requested short test, and the installed digest matches the recovery artifact. They clarified that ad cards remain below the video / suggested videos. A 165-second local snapshot records zero display replacements, zero filter errors and a maximum main-looper delay of 2ms. This shows that the narrow filter did not replace a component during that sample; whether its hook ran with missing identifiers/paths or the remaining card uses another renderer is unresolved. Further below-player filtering investigation is needed.
