#!/usr/bin/env python3
"""Drive a booted emulator through install, a page, tabs, and Ask Skim.

The emulator must already be the only device adb sees, or ANDROID_SERIAL must name it.
Evidence is written under VERIFY_EVIDENCE (default /tmp/skim-verify-evidence) and is not deleted.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = Path(os.environ.get("VERIFY_EVIDENCE", "/tmp/skim-verify-evidence"))
PACKAGE = "com.dantech.skimbrowser"
ACTIVITY = f"{PACKAGE}/.MainActivity"
ARTICLE = "http://127.0.0.1:8787/article"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args: str, check: bool = True, timeout: int = 60) -> subprocess.CompletedProcess[str]:
    serial = os.environ.get("ANDROID_SERIAL")
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    try:
        return subprocess.run(cmd, check=check, text=True, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"timed out: {' '.join(cmd)}") from error


def log(step: str) -> None:
    print(step, flush=True)


def dump_ui() -> ET.Element:
    last = ""
    remote = "/data/local/tmp/skim-ui.xml"
    pulled = EVIDENCE / "uidump.xml"
    for _ in range(8):
        result = adb("shell", "uiautomator", "dump", remote, check=False, timeout=40)
        last = (result.stdout or "") + (result.stderr or "")
        if result.returncode != 0 or "ERROR" in last.upper():
            time.sleep(1)
            continue
        cat = adb("exec-out", "cat", remote, check=False, timeout=20)
        if cat.returncode != 0 or not cat.stdout.strip().startswith("<?xml"):
            time.sleep(1)
            last = cat.stderr or last
            continue
        pulled.write_text(cat.stdout, encoding="utf-8")
        try:
            root = ET.fromstring(cat.stdout)
        except ET.ParseError:
            time.sleep(1)
            continue
        if sum(1 for _ in root.iter("node")) < 2:
            time.sleep(1)
            continue
        return root
    raise RuntimeError(f"uiautomator dump failed: {last.strip()}")


def node_center(root: ET.Element, *, text: str | None = None, desc: str | None = None, contains: bool = False) -> tuple[int, int] | None:
    for node in root.iter("node"):
        value = node.attrib.get("content-desc" if desc else "text", "")
        expected = desc if desc else text
        assert expected is not None
        matched = expected in value if contains else value == expected
        if not matched or not value:
            continue
        bounds = node.attrib.get("bounds", "")
        match = BOUNDS.fullmatch(bounds)
        if not match:
            continue
        x1, y1, x2, y2 = (int(group) for group in match.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def dismiss_blockers(root: ET.Element) -> bool:
    texts = [node.attrib.get("text", "") for node in root.iter("node")]
    if not any("isn't responding" in text for text in texts):
        return False
    center = node_center(root, text="Wait")
    if center is None:
        return False
    log("dismissed an unresponsive-process dialog")
    adb("shell", "input", "tap", str(center[0]), str(center[1]))
    time.sleep(1)
    return True


def wait_for(label: str, *, text: str | None = None, desc: str | None = None, contains: bool = False, timeout: int = 40) -> tuple[int, int]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            root = dump_ui()
        except RuntimeError:
            time.sleep(1)
            continue
        if dismiss_blockers(root):
            continue
        center = node_center(root, text=text, desc=desc, contains=contains)
        if center:
            log(f"found {label}")
            return center
        labels = []
        for node in root.iter("node"):
            shown = node.attrib.get("text") or node.attrib.get("content-desc") or ""
            if shown:
                labels.append(shown)
        log("seeing " + " | ".join(labels[:6]))
        if any(label in {"At a glance", "Gmail", "Home"} for label in labels):
            log("launcher is in front, starting Skim")
            adb("shell", "am", "start", "-n", ACTIVITY, check=False)
        time.sleep(1)
    raise RuntimeError(f"timed out waiting for {label}")


def tap(label: str, *, text: str | None = None, desc: str | None = None, contains: bool = False, timeout: int = 40) -> None:
    x, y = wait_for(label, text=text, desc=desc, contains=contains, timeout=timeout)
    last = ""
    for _ in range(12):
        result = adb("shell", "input", "tap", str(x), str(y), check=False, timeout=30)
        if result.returncode == 0:
            time.sleep(1)
            return
        last = (result.stderr or result.stdout or "").strip()
        log(f"tap waiting for input service: {last}")
        time.sleep(3)
    raise RuntimeError(f"input tap failed: {last}")


def shot(name: str) -> Path:
    path = EVIDENCE / name
    raw = subprocess.run(
        ["adb", *(["-s", os.environ["ANDROID_SERIAL"]] if os.environ.get("ANDROID_SERIAL") else []), "exec-out", "screencap", "-p"],
        check=True,
        capture_output=True,
        timeout=30,
    )
    path.write_bytes(raw.stdout)
    log(f"screenshot {path} ({path.stat().st_size} bytes)")
    return path


def doctor() -> None:
    state = adb("get-state").stdout.strip()
    if state != "device":
        raise SystemExit(f"adb state is {state!r}, expected device")
    packages = adb("shell", "pm", "path", PACKAGE, check=False).stdout
    reverse = adb("reverse", "--list", check=False).stdout
    log(f"adb state={state}")
    log(f"package={packages.strip() or 'not installed'}")
    log(f"reverse={reverse.strip() or 'none'}")


def journey(log_path: Path) -> None:
    """From the welcome screen through Ask Skim. The fake backend is already up."""
    wait_for("welcome", text="Get started", timeout=180)
    shot("01-welcome.png")
    tap("Get started", text="Get started", timeout=30)
    shot("02-setup.png")
    tap("Start browsing", text="Start browsing", timeout=40)
    shot("03-home.png")
    adb(
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.VIEW",
        "-d",
        ARTICLE,
        "-n",
        ACTIVITY,
    )
    wait_for("loaded page ask button", desc="Ask Skim about this page", timeout=180)
    shot("04-page.png")
    tap("tabs", desc="Tabs,", contains=True, timeout=40)
    shot("05-tabs.png")
    tap("new tab", desc="New tab", timeout=40)
    shot("06-new-tab.png")
    tap("tabs again", desc="Tabs,", contains=True, timeout=40)
    tap("article tab", text="heat", contains=True, timeout=40)
    tap("ask skim", desc="Ask Skim about this page", timeout=60)
    tap("allow", text="Allow", timeout=40)
    wait_for("summary text", text="minus 25", contains=True, timeout=150)
    shot("07-ask.png")
    frames = adb("shell", "dumpsys", "gfxinfo", PACKAGE, check=False, timeout=40).stdout
    (EVIDENCE / "gfxinfo.txt").write_text(frames, encoding="utf-8")
    backend = log_path.read_text(encoding="utf-8") if log_path.exists() else ""
    if not any(line.startswith("GET ") for line in backend.splitlines()):
        raise SystemExit(f"fake backend saw no page GET\n{backend}")
    if "POST /v1/messages" not in backend:
        raise SystemExit(f"fake backend saw no /v1/messages POST\n{backend}")
    if "x-api-key=None" not in backend:
        raise SystemExit(f"AI request carried an API key\n{backend}")
    if "X-Skim-Install-present=True" not in backend:
        raise SystemExit(f"AI request had no X-Skim-Install header\n{backend}")
    log("drive passed")
    log(f"evidence {EVIDENCE}")


def with_backend(body) -> None:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    sse = ROOT / "ai/src/testFixtures/resources/sse/summary-with-citations.sse"
    log_path = EVIDENCE / "fake-backend.log"
    server = subprocess.Popen(
        [sys.executable, str(ROOT / "tools/verify-skim/fake_backend.py"), "--port", "8787", "--sse", str(sse), "--log", str(log_path)],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    try:
        assert server.stdout is not None
        log(server.stdout.readline().strip())
        adb("reverse", "tcp:8787", "tcp:8787")
        body(log_path)
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
        adb("reverse", "--remove", "tcp:8787", check=False)


def drive() -> None:
    apk = Path(os.environ.get("VERIFY_APK", str(ROOT / "app/build/outputs/apk/releaseSmoke/app-releaseSmoke.apk")))
    if not apk.is_file():
        raise SystemExit(f"missing {apk}. Build :app:assembleReleaseSmoke first.")

    def body(log_path: Path) -> None:
        adb("install", "-r", "-d", str(apk), timeout=600)
        adb("shell", "pm", "clear", PACKAGE)
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset", check=False)
        adb("shell", "am", "start", "-n", ACTIVITY)
        journey(log_path)

    with_backend(body)


def from_welcome() -> None:
    """Continue a session that is already on the welcome screen."""

    def body(log_path: Path) -> None:
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset", check=False)
        journey(log_path)

    with_backend(body)


def from_setup() -> None:
    """Continue a session that is already on the setup screen. Welcome shots stay."""

    def body(log_path: Path) -> None:
        tap("Start browsing", text="Start browsing", timeout=180)
        shot("03-home.png")
        adb(
            "shell",
            "am",
            "start",
            "-a",
            "android.intent.action.VIEW",
            "-d",
            ARTICLE,
            "-n",
            ACTIVITY,
        )
        wait_for("loaded page ask button", desc="Ask Skim about this page", timeout=180)
        shot("04-page.png")
        tap("tabs", desc="Tabs,", contains=True, timeout=40)
        shot("05-tabs.png")
        tap("new tab", desc="New tab", timeout=40)
        shot("06-new-tab.png")
        tap("tabs again", desc="Tabs,", contains=True, timeout=40)
        tap("article tab", text="heat", contains=True, timeout=40)
        tap("ask skim", desc="Ask Skim about this page", timeout=60)
        tap("allow", text="Allow", timeout=40)
        wait_for("summary text", text="minus 25", contains=True, timeout=150)
        shot("07-ask.png")
        frames = adb("shell", "dumpsys", "gfxinfo", PACKAGE, check=False, timeout=40).stdout
        (EVIDENCE / "gfxinfo.txt").write_text(frames, encoding="utf-8")
        backend = log_path.read_text(encoding="utf-8") if log_path.exists() else ""
        if not any(line.startswith("GET ") for line in backend.splitlines()):
            raise SystemExit(f"fake backend saw no page GET\n{backend}")
        if "POST /v1/messages" not in backend:
            raise SystemExit(f"fake backend saw no /v1/messages POST\n{backend}")
        if "x-api-key=None" not in backend:
            raise SystemExit(f"AI request carried an API key\n{backend}")
        if "X-Skim-Install-present=True" not in backend:
            raise SystemExit(f"AI request had no X-Skim-Install header\n{backend}")
        log("drive passed")
        log(f"evidence {EVIDENCE}")

    with_backend(body)


def main() -> None:
    commands = {"doctor", "drive", "from-welcome", "from-setup"}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        raise SystemExit("usage: drive.py doctor|drive|from-welcome|from-setup")
    if sys.argv[1] == "doctor":
        doctor()
    elif sys.argv[1] == "from-welcome":
        from_welcome()
    elif sys.argv[1] == "from-setup":
        from_setup()
    else:
        drive()


if __name__ == "__main__":
    main()
