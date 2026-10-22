#!/usr/bin/env python3
"""Verify shipped feed classes using Android ART; does not launch the application.

Requires an Android host with app_process, JDK and D8. Supply a private APK.
Writable DEX/APKs are rejected by modern ART, so use a read-only private copy.
Exit 1 means a feed class failed; exit 2 means probe setup failed.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--d8-jar", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True, help="New private directory for probe outputs")
    parser.add_argument("--display-filter", action="store_true", help="Also verify the actual narrow component filter and its policy")
    parser.add_argument("--shorts-filter", action="store_true", help="Verify native Shorts fixtures, actual seed regression, and insertion owners")
    parser.add_argument("--background-playback", action="store_true", help="Verify the actual player's background eligibility and no-video guard")
    parser.add_argument("--original-apk", type=Path, help="Original APK for the intended pre-fix Shorts regression")
    args = parser.parse_args()
    if (args.shorts_filter or args.background_playback) and args.original_apk is None:
        parser.error("Shorts/background checks require --original-apk for the before/after regression")
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=False)
    apk = work / "target.apk"
    shutil.copyfile(args.apk, apk)
    apk.chmod(0o444)
    names = ["VerifyFeedClasses", "VerifyDisplayFilter", "VerifyShortsFilter"]
    if args.background_playback:
        names.append("VerifyBackgroundPlayback")
    subprocess.run(["javac", "--release", "8", "-Xlint:-options", "-d", str(work), *[str(ROOT / (name + ".java")) for name in names]], check=True)
    subprocess.run(["java", "-cp", str(args.d8_jar), "com.android.tools.r8.D8", "--min-api", "32", "--output", str(work), *[str(work / (name + ".class")) for name in names]], check=True)
    dex = work / "classes.dex"
    dex.chmod(0o444)
    runtime = Path("/system/bin/app_process")
    command = [str(runtime), "-Djava.class.path=" + str(dex), "/system/bin", "VerifyFeedClasses", str(apk), str(work)]
    if args.display_filter:
        command.append("--display-filter")
    result = subprocess.run(command, capture_output=True, text=True)
    output = result.stdout + result.stderr
    (work / "result.txt").write_text(output)
    print(output, end="")
    # No assertion-free success if the process dies before reaching both classes.
    required = ["PASS: ART verified and initialized " + name for name in ["alap", "alic"]]
    if args.display_filter:
        required += ["PASS: actual component filter replaces explicit ads", "PASS: ART verified and initialized vin", "PASS: actual component bridge preserves null inputs"]
    if args.shorts_filter or args.background_playback:
        original = work / "original.apk"
        shutil.copyfile(args.original_apk, original)
        original.chmod(0o444)
    if args.background_playback:
        background_command = [str(runtime), "-Djava.class.path=" + str(dex), "/system/bin", "VerifyBackgroundPlayback"]
        before = subprocess.run([*background_command, str(original), str(work)], capture_output=True, text=True)
        before_output = before.stdout + before.stderr
        (work / "background-before.txt").write_text(before_output)
        if before.returncode != 1 or "Active video is still rejected for missing server background-play eligibility" not in before_output:
            print("FAIL: original background-play regression did not fail for the intended eligibility reason")
            print(before_output, end="")
            return 2
        after = subprocess.run([*background_command, str(apk), str(work)], capture_output=True, text=True)
        after_output = after.stdout + after.stderr
        (work / "background-after.txt").write_text(after_output)
        print(after_output, end="")
        if after.returncode != 0 or "PASS: actual player controller permits background play" not in after_output:
            return after.returncode or 2
    if args.shorts_filter:
        shorts_command = [str(runtime), "-Djava.class.path=" + str(dex), "/system/bin", "VerifyShortsFilter"]
        before = subprocess.run([*shorts_command, str(original), str(work)], capture_output=True, text=True)
        before_output = before.stdout + before.stderr
        (work / "shorts-before.txt").write_text(before_output)
        print(before_output, end="")
        baseline_classifiers = "PASS: native APK protobuf classifiers distinguish ordinary and sponsored video/image Shorts"
        intended_failure = "FAIL: sponsored Shorts retained in seed (expected 2, actual 4)"
        if before.returncode != 1 or baseline_classifiers not in before_output or intended_failure not in before_output:
            print("FAIL: original Shorts regression did not fail for the intended retain-ad reason")
            return 2
        after = subprocess.run([*shorts_command, str(apk), str(work)], capture_output=True, text=True)
        after_output = after.stdout + after.stderr
        (work / "shorts-after.txt").write_text(after_output)
        print(after_output, end="")
        shorts_required = [baseline_classifiers,
            "PASS: actual seed constructors remove sponsored Shorts and preserve paired ordinary entries",
            "PASS: actual Shorts insertion filter preserves immutable ordinary and unknown inputs",
            *["PASS: ART verified and initialized " + name for name in ["asxq", "asyd", "asrd"]]]
        if after.returncode != 0 or not all(marker in after_output for marker in shorts_required):
            return after.returncode or 2
    if result.returncode == 0 and all(marker in output for marker in required):
        return 0
    return result.returncode or 2

if __name__ == "__main__":
    raise SystemExit(main())
