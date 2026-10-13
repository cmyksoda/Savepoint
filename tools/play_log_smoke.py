"""Check completion and replay history on a disposable sample-data emulator."""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--screenshots", type=Path, default=Path("design/qa"))
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    parser.error("This test modifies sample data and requires a disposable emulator")
args.screenshots.mkdir(parents=True, exist_ok=True)


def adb(*arguments):
    return subprocess.check_output(["adb", "-s", args.serial, *arguments], timeout=20)


def nodes():
    adb("shell", "rm", "-f", "/sdcard/savepoint-ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/savepoint-ui.xml")
    result = subprocess.run(
        ["adb", "-s", args.serial, "exec-out", "cat", "/sdcard/savepoint-ui.xml"],
        capture_output=True, timeout=20,
    )
    return list(ET.fromstring(result.stdout).iter("node")) if result.returncode == 0 else []


def wait_for(label, description=False):
    deadline = time.monotonic() + 25
    attribute = "content-desc" if description else "text"
    while time.monotonic() < deadline:
        for node in nodes():
            if node.get(attribute) == label:
                return node
    raise AssertionError(f"UI element missing: {label}")


def tap_node(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def tap(label, description=False):
    tap_node(wait_for(label, description))


def enter(label, text):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        for node in nodes():
            if node.get("class") == "android.widget.EditText" and any(
                child.get("text") == label for child in node.iter("node")
            ):
                tap_node(node)
                adb("shell", "input", "text", text)
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                return
    raise AssertionError(f"Editor missing: {label}")


def screenshot(name):
    (args.screenshots / name).write_bytes(adb("exec-out", "screencap", "-p"))


if not any(node.get("text") == "finished Rhythm Heaven Groove?" for node in nodes()):
    tap("Rhythm Heaven Groove, playing now", description=True)
    tap("mark finished")
tap("loved")
enter("thoughts (optional)", "thoughts-first-ui")
tap("finish & save")
wait_for("thoughts-first-ui")
tap("‹ back")
wait_for("thoughts-first-ui")
texts = [node.get("text", "") for node in nodes()]
assert not any("hours recorded" in text or re.fullmatch(r"\d+ replays?", text) for text in texts)
screenshot("play-log-no-extras.png")
tap("Rhythm Heaven Groove")
tap("replay now")
wait_for("Rhythm Heaven Groove, playing now", description=True)
tap("play log")
wait_for("thoughts-first-ui")
tap("Rhythm Heaven Groove")
tap("open current game")
tap("mark finished")
tap("liked")
enter("thoughts (optional)", "thoughts-replay-ui")
tap("✎ optional details")
enter("hours for this playthrough (optional)", "2.5")
enter("replays overall (optional)", "1")
tap("finish & save")
wait_for("thoughts-replay-ui")
wait_for("2.5 hours recorded")
wait_for("1 replay")
tap("earlier playthroughs")
wait_for("thoughts-first-ui")
screenshot("play-log-history.png")
adb("shell", "am", "force-stop", "com.cmyksoda.savepoint")
adb("shell", "am", "start", "-n", "com.cmyksoda.savepoint/.MainActivity")
tap("play log")
wait_for("thoughts-replay-ui")
tap("Rhythm Heaven Groove")
tap("earlier playthroughs")
wait_for("thoughts-first-ui")
print("PASS: completion thoughts, hidden blank extras, replay remains in log, separate replay thoughts, optional metrics, persisted history")
