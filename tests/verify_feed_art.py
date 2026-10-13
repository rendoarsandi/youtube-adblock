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
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=False)
    apk = work / "target.apk"
    shutil.copyfile(args.apk, apk)
    apk.chmod(0o444)
    subprocess.run(["javac", "--release", "8", "-Xlint:-options", "-d", str(work), str(ROOT / "VerifyFeedClasses.java"), str(ROOT / "VerifyDisplayFilter.java")], check=True)
    subprocess.run(["java", "-cp", str(args.d8_jar), "com.android.tools.r8.D8", "--min-api", "32", "--output", str(work), str(work / "VerifyFeedClasses.class"), str(work / "VerifyDisplayFilter.class")], check=True)
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
    if result.returncode == 0 and all(marker in output for marker in required):
        return 0
    return result.returncode or 2

if __name__ == "__main__":
    raise SystemExit(main())
