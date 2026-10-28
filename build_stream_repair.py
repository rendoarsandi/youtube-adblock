#!/usr/bin/env python3
"""Add the maintained stream repair to this project's exact v0.1.5 APK."""
import argparse
import json
from pathlib import Path
import re
import zipfile

import build
from tools import fix_resource_specs as specs
from tools.collect_patch_resource_flags import collect_added_entries

INPUT_SHA = "966029a148954bb8077855b1981a84f5e9cdd5ef6bdec2207aa1faec74e83827"
build.DEPENDENCIES.update({
    "morphe-desktop-1.19.1-all.jar": (
        "https://github.com/MorpheApp/morphe-desktop/releases/download/v1.19.1/morphe-desktop-1.19.1-all.jar",
        "a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573"),
    "patches-4.4.0-dev.3.mpp": (
        "https://github.com/anddea/revanced-patches/releases/download/v4.4.0-dev.3/patches-4.4.0-dev.3.mpp",
        "04c02b35bd48e8160fbc30519113659242073c3958dee954f60b22bc582eae57"),
})


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input-apk", required=True, type=Path)
    p.add_argument("--output-dir", required=True, type=Path)
    p.add_argument("--tool-cache", type=Path, default=build.ROOT / ".local-build/tools")
    p.add_argument("--keystore", type=Path)
    p.add_argument("--password-file", type=Path)
    p.add_argument("--key-alias", default="local-experiment")
    p.add_argument("--patched-apk", type=Path, help="Reuse a completed guarded patch run and its sibling patch-result.json")
    args = p.parse_args()
    source = args.input_apk.resolve()
    if build.sha256(source) != INPUT_SHA:
        raise ValueError("Requires the exact released v0.1.5 APK; no other target is accepted")
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=False)
    args.tool_cache.mkdir(parents=True, exist_ok=True)
    editor = build.dependency("APKEditor-1.4.9.jar", args.tool_cache)
    cli = build.dependency("morphe-desktop-1.19.1-all.jar", args.tool_cache)
    bundle = build.dependency("patches-4.4.0-dev.3.mpp", args.tool_cache)
    classes = out / "packaging-classes"
    classes.mkdir()
    helpers = build.ROOT / "tools/stream_compat"
    build.run(["javac", "-cp", editor, "-d", classes,
               helpers / "ManifestIdentity.java", helpers / "FinalizeYoutubeApk.java"])
    if args.patched_apk:
        patched = args.patched_apk.resolve()
    else:
        original_identity = out / "original-identity-input.apk"
        build.run(["java", "-Xmx512m", "-cp", str(classes) + ":" + str(editor), "ManifestIdentity",
                   source, original_identity, build.PACKAGE, "com.google.android.youtube"])
        with zipfile.ZipFile(source) as before, zipfile.ZipFile(original_identity) as after:
            if any(before.read(n) != after.read(n) for n in before.namelist() if n.endswith(".dex")):
                raise RuntimeError("Temporary identity preparation changed DEX code")
        export = "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
        build.run(["javac", "--add-exports", export, "-cp", cli, "-d", classes,
                   helpers / "AdaptStateFingerprint.java", helpers / "AdaptContentProviderFingerprint.java",
                   helpers / "ContentProviderDiagnosticAnywhere.java"])
        cp = str(classes) + ":" + str(cli)
        first = out / "state-adapted.mpp"
        adapted = out / "stream-repair.mpp"
        build.run(["java", "--add-exports", export, "-Xmx768m", "-cp", cp,
                   "AdaptStateFingerprint", bundle, original_identity, first], out / "state-adaptation.log")
        build.run(["java", "--add-exports", export, "-Xmx768m", "-cp", cp,
                   "AdaptContentProviderFingerprint", first, original_identity,
                   classes / "app/morphe/patches/local/ContentProviderDiagnosticAnywhere.class", adapted], out / "provider-adaptation.log")
        patched = out / "stream-patched-unsigned.apk"
        build.run(["java", "-Xmx1024m", "-jar", cli, "patch", "--force", "--exclusive", "--unsigned",
                   "--bytecode-mode=FULL", "-p", adapted, "-e", "Spoof video streams", original_identity,
                   "-o", patched, "-r", out / "patch-result.json", "-t", out / "scratch"], out / "patch.log")
    report = json.loads((patched.parent / "patch-result.json").read_text())
    if (not report.get("success") or report.get("failedPatches") or report.get("appliedPatches") != ["Spoof video streams"]
            or report.get("packageName") != "com.google.android.youtube" or report.get("packageVersion") != "21.40.161"):
        raise RuntimeError("Stream patch did not complete successfully")
    (out / "patch-result.json").write_text(json.dumps(report, indent=2) + "\n")
    packaged = out / "packaged.apk"
    build.run(["java", "-Xmx512m", "-cp", str(classes) + ":" + str(editor), "FinalizeYoutubeApk", patched, packaged])
    with zipfile.ZipFile(patched) as before, zipfile.ZipFile(packaged) as after:
        if any(before.read(n) != after.read(n) for n in before.namelist() if n.endswith(".dex")):
            raise RuntimeError("Packaging changed compiled playback code")
    for apk in [source, patched, packaged]:
        with zipfile.ZipFile(apk) as archive:
            resources = archive.read("resources.arsc")
            specs.collect(resources)
            collect_added_entries(resources)
    fixed = out / "resources-fixed.apk"
    with zipfile.ZipFile(packaged) as before, zipfile.ZipFile(fixed, "w") as after:
        resources, changes = specs.repair(before.read("resources.arsc"))
        for info in before.infolist():
            after.writestr(info, resources if info.filename == "resources.arsc" else before.read(info.filename))
    (out / "resource-repair.json").write_text(json.dumps(changes, indent=2) + "\n")
    aligned = out / "aligned.apk"
    build.run(["zipalign", "-P", "16", "-f", "4", fixed, aligned])
    key, password, alias = build.signing_key(args, out)
    apk = out / "YouTube-Adblock-v0.1.6.apk"
    build.run(["apksigner", "sign", "--ks", key, "--ks-key-alias", alias, "--ks-pass", "file:" + str(password), "--out", apk, aligned])
    sig = build.run(["apksigner", "verify", "--verbose", "--print-certs", apk], out / "signature.txt")
    original_sig = build.run(["apksigner", "verify", "--print-certs", source])
    def certificate(text):
        return re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", text).group(1)
    build.run(["zipalign", "-c", "-P", "16", "4", apk])
    badging = build.run(["aapt", "dump", "badging", apk], out / "badging.txt")
    if "name='app.revanced.android.youtube'" not in badging or "versionCode='1561301414'" not in badging or "application-label:'YouTube Adblock'" not in badging:
        raise RuntimeError("Unexpected final APK identity")
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(apk) as after:
        if after.testzip():
            raise RuntimeError("APK archive CRC failure")
        for name in before.namelist():
            if name.startswith(("lib/", "assets/")) and not name.startswith("assets/dexopt/") and before.read(name) != after.read(name):
                raise RuntimeError("Original native library or asset changed: " + name)
    metadata = {"package": build.PACKAGE, "version": "21.40.161", "patch_version": "v0.1.6",
                "apk_sha256": build.sha256(apk), "apk_bytes": apk.stat().st_size,
                "same_signer_as_v015": certificate(sig) == certificate(original_sig),
                "preserved_custom_ad_and_background_patches": True, "live_playback_verified": False}
    (out / "validation.json").write_text(json.dumps(metadata, indent=2) + "\n")
    (out / "SHA256SUMS.txt").write_text(build.sha256(apk) + "  " + apk.name + "\n")
    print(apk)


if __name__ == "__main__":
    main()
