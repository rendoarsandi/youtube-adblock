#!/usr/bin/env python3
"""Check companion XML through Android's parser, without launching YouTube.

Optional --baseline checks that every other attribute/node stays unchanged.
Exit 1 means the resource contract failed; exit 2 means probe setup failed.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--d8-jar", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=False)
    target = work / "target.apk"
    shutil.copyfile(args.apk, target)
    target.chmod(0o444)
    baseline = None
    if args.baseline:
        baseline = work / "baseline.apk"
        shutil.copyfile(args.baseline, baseline)
        baseline.chmod(0o444)
    subprocess.run(["javac", "--release", "8", "-Xlint:-options", "-d", str(work),
                    str(ROOT / "VerifyCompanionLayouts.java")], check=True)
    subprocess.run(["java", "-cp", str(args.d8_jar), "com.android.tools.r8.D8", "--min-api", "32",
                    "--output", str(work), str(work / "VerifyCompanionLayouts.class")], check=True)
    dex = work / "classes.dex"
    dex.chmod(0o444)
    command = ["/system/bin/app_process", "-Djava.class.path=" + str(dex), "/system/bin",
               "VerifyCompanionLayouts", str(target)]
    if baseline:
        command.append(str(baseline))
    result = subprocess.run(command, capture_output=True, text=True)
    output = result.stdout + result.stderr
    (work / "result.txt").write_text(output)
    print(output, end="")
    if result.returncode == 0 and "PASS: Android resolved dedicated companion nodes as collapsed" in output:
        return 0
    return result.returncode or 2


if __name__ == "__main__":
    raise SystemExit(main())
