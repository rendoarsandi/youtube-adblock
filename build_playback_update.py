#!/usr/bin/env python3
"""Correct v0.1.6 playback defaults and add bounded local error capture."""
import argparse
import json
from pathlib import Path
import re
import zipfile

import build
from tools import fix_resource_specs as specs
from tools.collect_patch_resource_flags import collect_added_entries

INPUT_SHA = "dc0f8be961a112b0f290319156e185ed478481b54f228188f5186009a3b86955"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--d8-jar", type=Path, required=True)
    parser.add_argument("--tool-cache", type=Path, default=build.ROOT / ".local-build/tools")
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--password-file", type=Path)
    parser.add_argument("--key-alias", default="local-experiment")
    args = parser.parse_args()
    source = args.input_apk.resolve()
    if build.sha256(source) != INPUT_SHA:
        raise ValueError("Requires the exact v0.1.6 APK")
    if not args.keystore and not (Path.home() / ".local/share/youtube-adblock/signing/local-signing.p12").is_file():
        raise ValueError("Existing update signing key is required; refusing to rotate it")
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=False)
    args.tool_cache.mkdir(parents=True, exist_ok=True)
    editor = build.dependency("APKEditor-1.4.9.jar", args.tool_cache)
    revanced = build.dependency("revanced-cli-6.0.0-all.jar", args.tool_cache)
    helper_classes, helper_dex, builder = out / "helper-classes", out / "helper-dex", out / "builder"
    for directory in [helper_classes, helper_dex, builder]:
        directory.mkdir()
    build.run(["javac", "--release", "8", "-Xlint:-options", "-d", helper_classes,
               build.ROOT / "src/playback/helper-src/app/local/youtube/adblock/PlaybackDiagnostics.java"])
    build.run(["java", "-cp", args.d8_jar.resolve(), "com.android.tools.r8.D8", "--min-api", "32", "--output", helper_dex,
               *sorted(helper_classes.rglob("*.class"))])
    classpath = str(revanced) + ":" + str(editor)
    build.run(["javac", "-cp", classpath, "-d", builder, build.ROOT / "src/NonRootYoutube.java",
               build.ROOT / "src/BackgroundPlayback.java", build.ROOT / "src/CompanionLayouts.java",
               build.ROOT / "tools/stream_compat/EnablePlayback.java"])
    patched = out / "patched"
    build.run(["java", "-Xmx768m", "-cp", str(builder) + ":" + classpath, "EnablePlayback",
               source, patched, helper_dex / "classes.dex"], out / "integration.log")
    raw = patched / "updated-unsigned.apk"
    if (patched / "hook-count.txt").read_text() != "7":
        raise RuntimeError("Integration did not apply all guarded targets")
    for apk in [source, raw]:
        with zipfile.ZipFile(apk) as archive:
            data = archive.read("resources.arsc")
            specs.collect(data)
            collect_added_entries(data)
    fixed = out / "resources-fixed.apk"
    with zipfile.ZipFile(raw) as before, zipfile.ZipFile(fixed, "w") as after:
        resources, changes = specs.repair(before.read("resources.arsc"))
        for info in before.infolist():
            after.writestr(info, resources if info.filename == "resources.arsc" else before.read(info.filename))
    (out / "resource-repair.json").write_text(json.dumps(changes, indent=2) + "\n")
    aligned = out / "aligned.apk"
    build.run(["zipalign", "-P", "16", "-f", "4", fixed, aligned])
    key, password, alias = build.signing_key(args, out)
    apk = out / "YouTube-Adblock-v0.1.7.apk"
    build.run(["apksigner", "sign", "--ks", key, "--ks-key-alias", alias, "--ks-pass", "file:" + str(password), "--out", apk, aligned])
    signature = build.run(["apksigner", "verify", "--verbose", "--print-certs", apk], out / "signature.txt")
    old_signature = build.run(["apksigner", "verify", "--print-certs", source])
    def certificate(text):
        return re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", text).group(1)
    if certificate(signature) != certificate(old_signature):
        raise RuntimeError("Update certificate changed")
    build.run(["zipalign", "-c", "-P", "16", "4", apk])
    badging = build.run(["aapt", "dump", "badging", apk], out / "badging.txt")
    if "name='app.revanced.android.youtube'" not in badging or "versionCode='1561301415'" not in badging:
        raise RuntimeError("Unexpected update identity")
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(apk) as after:
        if after.testzip():
            raise RuntimeError("APK archive CRC failure")
        for name in before.namelist():
            if name.startswith(("res/", "lib/", "assets/")) and before.read(name) != after.read(name):
                raise RuntimeError("Unexpected resource/native asset change: " + name)
    metadata = {"package": build.PACKAGE, "version": "21.40.161", "patch_version": "v0.1.7",
                "apk_sha256": build.sha256(apk), "apk_bytes": apk.stat().st_size, "same_signer_as_v016": True,
                "gms_support_status_corrected": True, "bounded_local_error_capture": True, "live_playback_verified": False}
    (out / "validation.json").write_text(json.dumps(metadata, indent=2) + "\n")
    (out / "SHA256SUMS.txt").write_text(build.sha256(apk) + "  " + apk.name + "\n")
    print(apk)


if __name__ == "__main__":
    main()
