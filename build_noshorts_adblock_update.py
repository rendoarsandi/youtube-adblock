#!/usr/bin/env python3
"""Build v0.1.9 (No Shorts + Full Litho Adblock) on top of v0.1.8."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

import build
from tools import fix_resource_specs as specs
from tools.collect_patch_resource_flags import collect_added_entries


ROOT = Path(__file__).resolve().parent
LOCAL_BUILD = ROOT / ".local-build"
TOOLS_DIR = LOCAL_BUILD / "tools"
REVANCED_JAR = TOOLS_DIR / "revanced-cli-6.0.0-all.jar"
APKEDITOR_JAR = TOOLS_DIR / "APKEditor-1.4.9.jar"
CLASSPATH = f"{REVANCED_JAR}:{APKEDITOR_JAR}"
DEFAULT_INPUT_APK = Path("/sdcard/Download/YouTube-Adblock-v0.1.8.apk")
INPUT_SHA = "5367afc0dfedf43abac5d77037ba64dfea94be26015d5bda3a4a9d8acce2d52f"
OUTPUT_DIR = LOCAL_BUILD / "youtube-noshorts-v019-final"
OUTPUT_APK = OUTPUT_DIR / "YouTube-Adblock-v0.1.9.apk"
DOWNLOAD_APK = Path("/sdcard/Download/YouTube-Adblock-v0.1.9.apk")


def run(cmd: list[str], *, cwd: Path | None = None) -> str:
    print("+", " ".join(str(x) for x in cmd), flush=True)
    completed = subprocess.run(
        [str(x) for x in cmd],
        cwd=str(cwd or ROOT),
        text=True,
        capture_output=True,
    )
    if completed.stdout:
        print(completed.stdout, end="", flush=True)
    if completed.stderr:
        print(completed.stderr, end="", flush=True)
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed ({completed.returncode}): {' '.join(str(x) for x in cmd)}")
    return completed.stdout + completed.stderr


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-apk", type=Path, default=DEFAULT_INPUT_APK)
    parser.add_argument(
        "--d8-jar",
        type=Path,
        default=Path(os.environ.get("PREFIX", "/data/data/com.termux/files/usr")) / "share/java/d8.jar",
    )
    parser.add_argument("--skip-download-copy", action="store_true")
    args = parser.parse_args()

    base_apk = args.input_apk.resolve()
    if not base_apk.is_file():
        raise RuntimeError(f"Missing input APK: {base_apk}")
    actual_sha = sha256(base_apk)
    if actual_sha != INPUT_SHA:
        raise RuntimeError(f"Input APK SHA-256 mismatch: {actual_sha} != {INPUT_SHA}")

    TOOLS_DIR.mkdir(parents=True, exist_ok=True)
    build.dependency("revanced-cli-6.0.0-all.jar", TOOLS_DIR)
    build.dependency("APKEditor-1.4.9.jar", TOOLS_DIR)

    java = shutil.which("java")
    javac = shutil.which("javac")
    if not java or not javac:
        raise RuntimeError("java and javac are required")

    with tempfile.TemporaryDirectory(prefix="yt-v019-") as tmp:
        work_dir = Path(tmp)
        helper_classes = work_dir / "helper-classes"
        helper_dex_dir = work_dir / "helper-dex"
        build_classes = work_dir / "build-classes"
        for d in (helper_classes, helper_dex_dir, build_classes):
            d.mkdir(parents=True, exist_ok=True)

        # 1. Compile updated Java helpers -> DEX
        run([
            javac, "--release", "8", "-Xlint:-options", "-d", str(helper_classes),
            str(ROOT / "src/playback/helper-src/app/local/youtube/adblock/DisplayAdPolicy.java"),
            str(ROOT / "src/playback/helper-src/app/local/youtube/adblock/ShortsBlocker.java"),
        ])
        run([
            java, "-cp", str(args.d8_jar.resolve()), "com.android.tools.r8.D8",
            "--min-api", "32", "--output", str(helper_dex_dir),
            *[str(p) for p in sorted(helper_classes.rglob("*.class"))],
        ])

        # 2. Compile patcher & APK updater
        updater_java = work_dir / "UpdateApkV019.java"
        updater_java.write_text(
            r"""
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdateApkV019 {
    public static void main(String[] args) throws Exception {
        Path inApk = Path.of(args[0]);
        Path outApk = Path.of(args[1]);
        try (ApkModule module = ApkModule.loadApkFile(inApk.toFile())) {
            var manifest = module.getAndroidManifest();
            if (!manifest.getPackageName().equals("app.revanced.android.youtube")
                || manifest.getVersionCode() != 1561301416) {
                throw new IllegalStateException("Unexpected input manifest: "
                    + manifest.getPackageName() + " vc=" + manifest.getVersionCode());
            }
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[2])), "classes2.dex"));
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[3])), "classes5.dex"));
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[4])), "classes9.dex"));
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[5])), "classes11.dex"));
            manifest.setVersionCode(1561301417);
            module.setApkSignatureBlock(null);
            module.removeInputSource("stamp-cert-sha256");
            for (var source : module.listInputSources()) {
                if (source.getAlias().matches("META-INF/(?:MANIFEST\\.MF|[^/]+\\.(?:SF|RSA|DSA|EC))")) {
                    module.removeInputSource(source.getAlias());
                }
            }
            module.refreshManifest();
            module.refreshTable();
            module.writeApk(outApk.toFile());
        }
    }
}
""".strip() + "\n",
            encoding="utf-8",
        )
        run([
            javac, "-cp", CLASSPATH, "-d", str(build_classes),
            str(ROOT / "tools/stream_compat/EnableNoShortsAndAdblock.java"),
            str(updater_java),
        ])

        # 3. Extract & patch target DEX files
        dex_names = ["classes2.dex", "classes5.dex", "classes9.dex", "classes11.dex"]
        with zipfile.ZipFile(base_apk, "r") as zf:
            for name in dex_names:
                (work_dir / f"{name}.in").write_bytes(zf.read(name))

        run([
            java, "-Xmx1024m", "-cp", f"{build_classes}:{CLASSPATH}",
            "EnableNoShortsAndAdblock",
            str(work_dir / "classes2.dex.in"), str(work_dir / "classes2.dex"),
            str(work_dir / "classes5.dex.in"), str(work_dir / "classes5.dex"),
            str(work_dir / "classes9.dex.in"), str(work_dir / "classes9.dex"),
            str(work_dir / "classes11.dex.in"), str(work_dir / "classes11.dex"),
            str(helper_dex_dir / "classes.dex"),
        ])

        # 4. Repackage APK, repair resources.arsc, zipalign, sign
        raw_apk = work_dir / "updated-unsigned.apk"
        run([
            java, "-Xmx1024m", "-cp", f"{build_classes}:{CLASSPATH}",
            "UpdateApkV019",
            str(base_apk), str(raw_apk),
            str(work_dir / "classes2.dex"),
            str(work_dir / "classes5.dex"),
            str(work_dir / "classes9.dex"),
            str(work_dir / "classes11.dex"),
        ])

        for apk in (base_apk, raw_apk):
            with zipfile.ZipFile(apk) as archive:
                data = archive.read("resources.arsc")
                specs.collect(data)
                collect_added_entries(data)

        fixed_apk = work_dir / "resources-fixed.apk"
        with zipfile.ZipFile(raw_apk) as before, zipfile.ZipFile(fixed_apk, "w") as after:
            resources, changes = specs.repair(before.read("resources.arsc"))
            for info in before.infolist():
                after.writestr(info, resources if info.filename == "resources.arsc" else before.read(info.filename))

        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        (OUTPUT_DIR / "resource-repair.json").write_text(json.dumps(changes, indent=2) + "\n", encoding="utf-8")

        aligned_apk = work_dir / "aligned.apk"
        run(["zipalign", "-P", "16", "-f", "4", str(fixed_apk), str(aligned_apk)])

        dummy_args = argparse.Namespace(keystore=None, password_file=None, key_alias="local-experiment")
        key, password, alias = build.signing_key(dummy_args, OUTPUT_DIR)
        run([
            "apksigner", "sign",
            "--ks", str(key),
            "--ks-key-alias", str(alias),
            "--ks-pass", "file:" + str(password),
            "--out", str(OUTPUT_APK),
            str(aligned_apk),
        ])

        # 5. Verify signature match, alignment, badging, CRC, and unchanged assets
        signature = run(["apksigner", "verify", "--verbose", "--print-certs", str(OUTPUT_APK)])
        (OUTPUT_DIR / "signature.txt").write_text(signature, encoding="utf-8")
        old_signature = run(["apksigner", "verify", "--print-certs", str(base_apk)])

        def cert_sha(text: str) -> str:
            m = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", text)
            if not m:
                raise RuntimeError("Could not parse certificate SHA-256 digest")
            return m.group(1)

        if cert_sha(signature) != cert_sha(old_signature):
            raise RuntimeError("Update certificate changed!")

        run(["zipalign", "-c", "-P", "16", "4", str(OUTPUT_APK)])
        badging = run(["aapt", "dump", "badging", str(OUTPUT_APK)])
        (OUTPUT_DIR / "badging.txt").write_text(badging, encoding="utf-8")
        if "name='app.revanced.android.youtube'" not in badging or "versionCode='1561301417'" not in badging:
            raise RuntimeError("Unexpected update identity")

        with zipfile.ZipFile(base_apk) as before, zipfile.ZipFile(OUTPUT_APK) as after:
            if after.testzip():
                raise RuntimeError("APK archive CRC failure")
            for name in before.namelist():
                if name.startswith(("res/", "lib/", "assets/")) and before.read(name) != after.read(name):
                    raise RuntimeError("Unexpected resource/native asset change: " + name)

        out_sha = sha256(OUTPUT_APK)
        metadata = {
            "package": build.PACKAGE,
            "version": "21.40.161",
            "patch_version": "v0.1.9",
            "version_code": 1561301417,
            "apk_sha256": out_sha,
            "apk_bytes": OUTPUT_APK.stat().st_size,
            "same_signer_as_v018": True,
            "changes": [
                "Registered AdsFilter and ShortsShelfFilter in LithoFilterPatch.patch_getFilterArray()",
                "Enabled HIDE_SHORTS_SHELF, HIDE_SHORTS_SHELF_CHANNEL, and HIDE_SHORTS_SHELF_HISTORY defaults",
                "Expanded DisplayAdPolicy with full Litho ad + Shorts component patterns",
                "Hidden Shorts bottom-nav PivotBar tab via ShortsBlocker.hideShortsTab in Lrrg.P(Z)V",
                "Disabled Shorts player entry via early return in Lkym.b(Lbdfp;Ljava/util/Map;)V",
            ],
        }
        (OUTPUT_DIR / "validation.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        (OUTPUT_DIR / "SHA256SUMS.txt").write_text(f"{out_sha}  {OUTPUT_APK.name}\n", encoding="utf-8")

        if not args.skip_download_copy:
            shutil.copyfile(OUTPUT_APK, DOWNLOAD_APK)
            print(f"Copied signed APK to {DOWNLOAD_APK}")

        print(f"BUILT_OK {OUTPUT_APK} sha256={out_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
