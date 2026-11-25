#!/usr/bin/env python3
"""Build v0.1.10 (SponsorBlock skip + seekbar segments + disable diagnostics) on top of v0.1.9."""
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
DEFAULT_INPUT_APK = LOCAL_BUILD / "youtube-noshorts-v019-final/YouTube-Adblock-v0.1.9.apk"
INPUT_SHA = "12730ca6cd102268a395a173cc73609005c857f085111423d8ef40f84fd4ef93"
OUTPUT_DIR = LOCAL_BUILD / "youtube-sponsorblock-v0110-final"
OUTPUT_APK = OUTPUT_DIR / "YouTube-Adblock-v0.1.10.apk"
DOWNLOAD_APK = Path("/sdcard/Download/YouTube-Adblock-v0.1.10.apk")


COMPILE_STUBS: dict[str, str] = {
    "android/content/Context.java": """
package android.content;
import android.content.res.Resources;
public class Context {
    public Resources getResources() { return null; }
}
""",
    "android/content/res/Resources.java": """
package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    public DisplayMetrics getDisplayMetrics() { return null; }
}
""",
    "android/util/DisplayMetrics.java": """
package android.util;
public class DisplayMetrics {
    public float density;
}
""",
    "android/util/AttributeSet.java": """
package android.util;
public interface AttributeSet {}
""",
    "android/util/TypedValue.java": """
package android.util;
public class TypedValue {
    public static final int COMPLEX_UNIT_DIP = 1;
    public static final int COMPLEX_UNIT_SP = 2;
}
""",
    "android/graphics/Canvas.java": """
package android.graphics;
public class Canvas {
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {}
}
""",
    "android/graphics/Paint.java": """
package android.graphics;
public class Paint {}
""",
    "android/graphics/Rect.java": """
package android.graphics;
public class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;
}
""",
    "android/graphics/Typeface.java": """
package android.graphics;
public class Typeface {}
""",
    "android/graphics/drawable/Drawable.java": """
package android.graphics.drawable;
public abstract class Drawable {}
""",
    "android/graphics/drawable/GradientDrawable.java": """
package android.graphics.drawable;
public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0;
    public GradientDrawable() {}
    public void setShape(int shape) {}
    public void setColor(int argb) {}
    public void setCornerRadius(float radius) {}
    public void setStroke(int width, int color) {}
}
""",
    "android/view/Gravity.java": """
package android.view;
public class Gravity {
    public static final int CENTER_VERTICAL = 16;
    public static final int CENTER = 17;
}
""",
    "android/view/View.java": """
package android.view;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
public class View {
    public static final int VISIBLE = 0;
    public static final int GONE = 8;
    public interface OnClickListener {
        void onClick(View v);
    }
    public View(Context c) {}
    public View(Context c, AttributeSet a) {}
    public View(Context c, AttributeSet a, int d) {}
    public View(Context c, AttributeSet a, int d, int r) {}
    public Context getContext() { return null; }
    public void setVisibility(int visibility) {}
    public int getVisibility() { return 0; }
    public void setClickable(boolean clickable) {}
    public void setFocusable(boolean focusable) {}
    public void setPadding(int l, int t, int r, int b) {}
    public void setMinimumHeight(int minHeight) {}
    public void setBackground(Drawable bg) {}
    public void setElevation(float elevation) {}
    public void setTranslationZ(float z) {}
    public void setLayoutParams(ViewGroup.LayoutParams params) {}
    public ViewGroup.LayoutParams getLayoutParams() { return null; }
    public void setOnClickListener(OnClickListener l) {}
    public void bringToFront() {}
    protected void dispatchDraw(Canvas canvas) {}
}
""",
    "android/view/ViewGroup.java": """
package android.view;
import android.content.Context;
import android.util.AttributeSet;
public abstract class ViewGroup extends View {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public LayoutParams(int width, int height) {}
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;
        public MarginLayoutParams(int width, int height) { super(width, height); }
    }
    public interface OnHierarchyChangeListener {
        void onChildViewAdded(View parent, View child);
        void onChildViewRemoved(View parent, View child);
    }
    public ViewGroup(Context c) { super(c); }
    public ViewGroup(Context c, AttributeSet a) { super(c, a); }
    public ViewGroup(Context c, AttributeSet a, int d) { super(c, a, d); }
    public ViewGroup(Context c, AttributeSet a, int d, int r) { super(c, a, d, r); }
    public void addView(View child) {}
    public void addView(View child, LayoutParams params) {}
    public void setClipChildren(boolean clip) {}
    public void setClipToPadding(boolean clip) {}
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {}
}
""",
    "android/widget/FrameLayout.java": """
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
    public FrameLayout(Context c) { super(c); }
    public FrameLayout(Context c, AttributeSet a) { super(c, a); }
    public FrameLayout(Context c, AttributeSet a, int d) { super(c, a, d); }
    public FrameLayout(Context c, AttributeSet a, int d, int r) { super(c, a, d, r); }
}
""",
    "android/widget/LinearLayout.java": """
package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
    public LinearLayout(Context c) { super(c); }
    public void setOrientation(int orientation) {}
    public void setGravity(int gravity) {}
}
""",
    "android/widget/RelativeLayout.java": """
package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class RelativeLayout extends ViewGroup {
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int ALIGN_PARENT_END = 21;
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
        public void addRule(int verb) {}
    }
    public RelativeLayout(Context c) { super(c); }
}
""",
    "android/widget/TextView.java": """
package android.widget;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
public class TextView extends View {
    public TextView(Context c) { super(c); }
    public void setText(CharSequence text) {}
    public CharSequence getText() { return null; }
    public void setTextSize(int unit, float size) {}
    public void setTextColor(int color) {}
    public void setSingleLine(boolean singleLine) {}
    public void setGravity(int gravity) {}
    public void setTypeface(Typeface tf, int style) {}
}
""",
    "app/morphe/extension/shared/settings/BooleanSetting.java": """
package app.morphe.extension.shared.settings;
public class BooleanSetting {
    public Boolean get() { return Boolean.FALSE; }
}
""",
    "app/morphe/extension/shared/utils/Utils.java": """
package app.morphe.extension.shared.utils;
public class Utils {
    public static boolean isCurrentlyOnMainThread() { return false; }
    public static void runOnMainThread(Runnable r) {}
}
""",
    "app/morphe/extension/youtube/settings/Settings.java": """
package app.morphe.extension.youtube.settings;
import app.morphe.extension.shared.settings.BooleanSetting;
public class Settings {
    public static final BooleanSetting SB_ENABLED = null;
    public static final BooleanSetting SB_SQUARE_LAYOUT = null;
}
""",
    "app/morphe/extension/youtube/shared/PlayerType.java": """
package app.morphe.extension.youtube.shared;
public enum PlayerType {
    NONE,
    WATCH_WHILE_MAXIMIZED,
    WATCH_WHILE_FULLSCREEN;
    public static PlayerType getCurrent() { return NONE; }
}
""",
    "app/morphe/extension/youtube/shared/RootView.java": """
package app.morphe.extension.youtube.shared;
public final class RootView {
    public static boolean isShortsActive() { return false; }
    public static boolean isAdProgressTextVisible() { return false; }
}
""",
    "app/morphe/extension/youtube/shared/VideoInformation.java": """
package app.morphe.extension.youtube.shared;
public final class VideoInformation {
    public static String getChannelId() { return ""; }
    public static String getChannelName() { return ""; }
    public static String getVideoTitle() { return ""; }
    public static long getVideoLength() { return 0L; }
    public static boolean getLiveStreamState() { return false; }
}
""",
    "app/morphe/extension/youtube/sponsorblock/objects/SegmentCategory.java": """
package app.morphe.extension.youtube.sponsorblock.objects;
import android.graphics.Paint;
public enum SegmentCategory {
    SPONSOR,
    HIGHLIGHT;
    public Paint paint;
}
""",
    "app/morphe/extension/youtube/sponsorblock/objects/SponsorSegment.java": """
package app.morphe.extension.youtube.sponsorblock.objects;
public class SponsorSegment {
    public final SegmentCategory category;
    public final long start;
    public final long end;
    public SponsorSegment() { this.category = null; this.start = 0L; this.end = 0L; }
    public String getSkipButtonText() { return ""; }
}
""",
    "app/morphe/extension/youtube/sponsorblock/SponsorBlockSettings.java": """
package app.morphe.extension.youtube.sponsorblock;
public class SponsorBlockSettings {
    public static void initialize() {}
}
""",
    "app/morphe/extension/youtube/sponsorblock/SegmentPlaybackController.java": """
package app.morphe.extension.youtube.sponsorblock;
import app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment;
public class SegmentPlaybackController {
    public static void initialize() {}
    public static void newVideoStarted(String cid, String cname, String vid, String title, long len, boolean live) {}
    public static void setVideoTime(long time) {}
    public static SponsorSegment[] getSegments() { return null; }
    public static void onSkipSegmentClicked(SponsorSegment seg) {}
}
""",
    "app/morphe/extension/youtube/sponsorblock/ui/SponsorBlockViewController.java": """
package app.morphe.extension.youtube.sponsorblock.ui;
public class SponsorBlockViewController {
    public static void hideAll() {}
}
""",
}


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

    with tempfile.TemporaryDirectory(prefix="yt-v0110-") as tmp:
        work_dir = Path(tmp)
        stub_src = work_dir / "stub-src"
        stub_classes = work_dir / "stub-classes"
        helper_classes = work_dir / "helper-classes"
        helper_dex_dir = work_dir / "helper-dex"
        build_classes = work_dir / "build-classes"
        for d in (stub_src, stub_classes, helper_classes, helper_dex_dir, build_classes):
            d.mkdir(parents=True, exist_ok=True)

        # 1. Compile compile-time-only stubs (never packaged into DEX)
        stub_files: list[str] = []
        for rel, content in COMPILE_STUBS.items():
            dest = stub_src / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content.strip() + "\n", encoding="utf-8")
            stub_files.append(str(dest))

        run([javac, "--release", "8", "-Xlint:-options", "-d", str(stub_classes), *stub_files])

        # 2. Compile our actual Java helpers -> DEX (excluding stubs!)
        helper_sources = [
            ROOT / "src/playback/helper-src/app/local/youtube/adblock/LocalDiagnostics.java",
            ROOT / "src/playback/helper-src/app/local/youtube/adblock/PlaybackDiagnostics.java",
            ROOT / "src/playback/helper-src/app/local/youtube/adblock/SponsorBlockBridge.java",
            ROOT / "src/playback/helper-src/app/local/youtube/adblock/SponsorBlockOverlayHelper.java",
            ROOT / "src/playback/helper-src/app/morphe/extension/youtube/sponsorblock/ui/SkipSponsorButton.java",
        ]
        run([
            javac, "--release", "8", "-Xlint:-options",
            "-cp", str(stub_classes),
            "-d", str(helper_classes),
            *[str(p) for p in helper_sources],
        ])

        compiled_helpers = sorted(helper_classes.rglob("*.class"))
        for p in compiled_helpers:
            rel = p.relative_to(helper_classes).as_posix()
            if rel.startswith("android/"):
                raise RuntimeError(f"Stub leaked into helper_classes: {rel}")

        run([
            java, "-cp", str(args.d8_jar.resolve()), "com.android.tools.r8.D8",
            "--min-api", "32", "--output", str(helper_dex_dir),
            *[str(p) for p in compiled_helpers],
        ])

        # 3. Compile DEX patcher & APK updater
        updater_java = work_dir / "UpdateApkV0110.java"
        updater_java.write_text(
            r"""
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdateApkV0110 {
    public static void main(String[] args) throws Exception {
        Path inApk = Path.of(args[0]);
        Path outApk = Path.of(args[1]);
        try (ApkModule module = ApkModule.loadApkFile(inApk.toFile())) {
            var manifest = module.getAndroidManifest();
            if (!manifest.getPackageName().equals("app.revanced.android.youtube")
                || manifest.getVersionCode() != 1561301417) {
                throw new IllegalStateException("Unexpected input manifest: "
                    + manifest.getPackageName() + " vc=" + manifest.getVersionCode());
            }
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[2])), "classes2.dex"));
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[3])), "classes9.dex"));
            module.add(new ByteInputSource(Files.readAllBytes(Path.of(args[4])), "classes11.dex"));
            manifest.setVersionCode(1561301418);
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
            str(ROOT / "tools/stream_compat/EnableSponsorBlockAndDisableDiagnostics.java"),
            str(updater_java),
        ])

        # 4. Extract & patch target DEX files
        dex_names = ["classes2.dex", "classes9.dex", "classes11.dex"]
        with zipfile.ZipFile(base_apk, "r") as zf:
            for name in dex_names:
                (work_dir / f"{name}.in").write_bytes(zf.read(name))

        run([
            java, "-Xmx1024m", "-cp", f"{build_classes}:{CLASSPATH}",
            "EnableSponsorBlockAndDisableDiagnostics",
            str(work_dir / "classes2.dex.in"), str(work_dir / "classes2.dex"),
            str(work_dir / "classes9.dex.in"), str(work_dir / "classes9.dex"),
            str(work_dir / "classes11.dex.in"), str(work_dir / "classes11.dex"),
            str(helper_dex_dir / "classes.dex"),
        ])

        # 5. Repackage APK, repair resources.arsc, zipalign, sign
        raw_apk = work_dir / "updated-unsigned.apk"
        run([
            java, "-Xmx1024m", "-cp", f"{build_classes}:{CLASSPATH}",
            "UpdateApkV0110",
            str(base_apk), str(raw_apk),
            str(work_dir / "classes2.dex"),
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

        # 6. Verify signature match, alignment, badging, CRC, and unchanged assets
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
        if "name='app.revanced.android.youtube'" not in badging or "versionCode='1561301418'" not in badging:
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
            "patch_version": "v0.1.10",
            "version_code": 1561301418,
            "apk_sha256": out_sha,
            "apk_bytes": OUTPUT_APK.stat().st_size,
            "same_signer_as_v019": True,
            "changes": [
                "Disabled LocalDiagnostics and PlaybackDiagnostics (no background threads or Download/YouTube-Adblock-Diagnostics files)",
                "Wired VideoInformation.{initialize,setVideoId,setVideoInformation,setVideoTime} to Morphe SponsorBlock via SponsorBlockBridge",
                "Attached programmatic SkipSponsorButton overlay to YouTubePlayerOverlaysLayout.onFinishInflate for manual-skip pills and auto-skip undo toast",
                "Rendered colored SponsorBlock segment bars on the seekbar in Ljsi.onDraw before the scrubber thumb",
                "Disabled SB_TOAST_ON_CONNECTION_ERROR by default to keep network hiccups silent",
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
