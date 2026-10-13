"""Exercise a disposable emulator library, never a connected physical phone."""

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
    parser.error("This test changes sample data and may only run on a disposable emulator")
args.screenshots.mkdir(parents=True, exist_ok=True)


def adb(*arguments):
    return subprocess.check_output(["adb", "-s", args.serial, *arguments], timeout=20)


def nodes():
    adb("shell", "rm", "-f", "/sdcard/savepoint-ui.xml")
    subprocess.run(["adb", "-s", args.serial, "shell", "uiautomator", "dump", "/sdcard/savepoint-ui.xml"],
                   capture_output=True, timeout=20)
    result = subprocess.run(
        ["adb", "-s", args.serial, "exec-out", "cat", "/sdcard/savepoint-ui.xml"],
        capture_output=True, timeout=20,
    )
    try:
        return list(ET.fromstring(result.stdout).iter("node")) if result.returncode == 0 else []
    except ET.ParseError:
        return []


def wait_for(label, description=False):
    deadline = time.monotonic() + 20
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


def enter_note(text):
    deadline = time.monotonic() + 20
    editor = None
    while editor is None and time.monotonic() < deadline:
        editor = next((node for node in nodes() if node.get("class") == "android.widget.EditText"), None)
    assert editor is not None, "Note editor missing"
    tap_node(editor)
    adb("shell", "input", "text", text)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")


def screenshot(name):
    nodes()
    time.sleep(0.35)
    (args.screenshots / name).write_bytes(adb("exec-out", "screencap", "-p"))


tap("Rhythm Heaven Groove, playing now", description=True)
tap("notes")
enter_note("general-note-ui")
tap("save")
tap("pause this game")
enter_note("pause-note-ui")
screenshot("pause-dialog.png")
tap("save & pause")
wait_for("DELTARUNE, playing now", description=True)
tap("paused")
tap("Rhythm Heaven Groove")
wait_for("pause-note-ui")
tap("notes")
wait_for("general-note-ui")
tap("cancel")
screenshot("paused-details.png")
tap("play now")
wait_for("Rhythm Heaven Groove, playing now", description=True)
tap("edit play through shelf", description=True)
tap("DELTARUNE")
tap("play now")
wait_for("DELTARUNE, playing now", description=True)
screenshot("swapped-focus.png")
adb("shell", "am", "force-stop", "com.cmyksoda.savepoint")
adb("shell", "am", "start", "-n", "com.cmyksoda.savepoint/.MainActivity")
wait_for("DELTARUNE, playing now", description=True)
print("PASS: independent notes, pause, focus promotion, resume, focus swap, process-restart persistence")
