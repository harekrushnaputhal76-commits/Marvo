"""
Marvo System Operations — modules/systemops.py
==============================================
Production-ready system utilities for Windows 10.
Optimized for low-resource hardware (4GB RAM, Intel Core 2 Duo).
Non-blocking execution using subprocess.Popen and background threads.

Prerequisite:
  pip install psutil
"""

import os
import re
import shutil
import logging
import threading
import subprocess
from typing import Tuple

try:
    import psutil
except ImportError:
    psutil = None

_logger = logging.getLogger("marvo.systemops")

# ──────────────────────────────────────────────────────────────────────
# APPLICATION COMMAND MAPPINGS (Windows Executables)
# ──────────────────────────────────────────────────────────────────────
APP_REGISTRY = {
    "calculator": "calc.exe",
    "calc": "calc.exe",
    "notepad": "notepad.exe",
    "taskmgr": "taskmgr.exe",
    "task_manager": "taskmgr.exe",
    "settings": "start ms-settings:",
    "cmd": "start cmd.exe",
    "terminal": "start cmd.exe",
    "vscode": "code",
    "code": "code",
    "libreoffice": "soffice",
    "soffice": "soffice",
    "browser": "start msedge",
    "chrome": "start chrome",
    "edge": "start msedge",
}


# ──────────────────────────────────────────────────────────────────────
# 1. SYSTEM RESOURCE MONITORING
# ──────────────────────────────────────────────────────────────────────
def get_system_stats() -> str:
    """
    Safely fetches current CPU utilization (%) and Available/Used RAM in GB.
    Returns a clean string formatted for TTS engines and UI display.
    """
    if psutil is None:
        return "System monitoring unavailable. psutil library is not installed."

    try:
        # Interval=0.1 keeps it quick and non-blocking
        cpu_percent = psutil.cpu_percent(interval=0.1)
        mem = psutil.virtual_memory()

        used_gb = round(mem.used / (1024 ** 3), 1)
        free_gb = round(mem.available / (1024 ** 3), 1)
        total_gb = round(mem.total / (1024 ** 3), 1)

        return (
            f"CPU usage is at {cpu_percent} percent, and {free_gb} GB of RAM is free "
            f"out of {total_gb} GB total."
        )
    except Exception as e:
        _logger.error(f"Error fetching system stats: {e}")
        return "Could not retrieve system statistics at the moment."


# ──────────────────────────────────────────────────────────────────────
# 2. ADVANCED NON-BLOCKING APP LAUNCHER
# ──────────────────────────────────────────────────────────────────────
def launch_application(app_name: str) -> str:
    """
    Launches a Windows desktop application non-blockingly using subprocess.Popen.
    The main Flask/AI thread is never frozen.

    Args:
        app_name: Target application key or executable name.

    Returns:
        Status message describing the launch result.
    """
    key = app_name.lower().strip()
    command = APP_REGISTRY.get(key, key)

    try:
        # Non-blocking subprocess launch on Windows
        subprocess.Popen(
            command,
            shell=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        return f"Opening {app_name.capitalize()} now."
    except FileNotFoundError:
        _logger.warning(f"Application command not found: {command}")
        return f"Could not find {app_name} installed on your system."
    except Exception as e:
        _logger.error(f"Error launching {app_name}: {e}")
        return f"Failed to launch {app_name}."


# ──────────────────────────────────────────────────────────────────────
# 3. WINDOWS JUNK & TEMP CLEANER
# ──────────────────────────────────────────────────────────────────────
def clean_windows_temp() -> str:
    """
    Cleans user-level temporary files from %TEMP% and %TMP%.
    Avoids C:\\Windows\\Temp to prevent administrator permission errors.
    Uses tight exception handling to bypass locked in-use files safely.

    Returns:
        Summary of deleted files and freed space.
    """
    temp_dirs = set()
    for env_var in ("TEMP", "TMP"):
        path = os.environ.get(env_var)
        if path and os.path.isdir(path):
            temp_dirs.add(os.path.abspath(path))

    if not temp_dirs:
        return "No temporary directories found to clean."

    deleted_files = 0
    deleted_dirs = 0
    freed_bytes = 0

    for temp_dir in temp_dirs:
        try:
            entries = os.listdir(temp_dir)
        except (PermissionError, OSError):
            continue

        for item in entries:
            item_path = os.path.join(temp_dir, item)
            try:
                if os.path.islink(item_path) or os.path.isfile(item_path):
                    try:
                        size = os.path.getsize(item_path)
                    except OSError:
                        size = 0
                    os.remove(item_path)
                    deleted_files += 1
                    freed_bytes += size
                elif os.path.isdir(item_path):
                    shutil.rmtree(item_path, ignore_errors=True)
                    deleted_dirs += 1
            except (PermissionError, FileNotFoundError, OSError):
                # File is currently locked or in-use by Windows/running apps
                pass

    total_deleted = deleted_files + deleted_dirs
    freed_mb = round(freed_bytes / (1024 * 1024), 1)

    if total_deleted > 0:
        return (
            f"Temporary files cleaned. Removed {total_deleted} items "
            f"and freed approximately {freed_mb} MB of disk space."
        )
    return "Temporary folder checked. No unlocked junk files needed cleaning."


# ──────────────────────────────────────────────────────────────────────
# 4. ACTION TAG PARSER
# ──────────────────────────────────────────────────────────────────────
def execute_action_tag(response_text: str) -> Tuple[str, str]:
    """
    Parses action tags formatted as [ACTION:<TYPE>] from the response string,
    executes the associated system action, and strips the tag from the text.

    Supported Tags:
      - [ACTION:OPEN_VSCODE]        -> launch_application('vscode')
      - [ACTION:OPEN_TASK_MANAGER]  -> launch_application('taskmgr')
      - [ACTION:OPEN_CALCULATOR]    -> launch_application('calculator')
      - [ACTION:CLEAN_WIN_TEMP]     -> clean_windows_temp()
      - [ACTION:CHECK_RAM]          -> get_system_stats()

    Returns:
        Tuple[str, str]: (cleaned_text_without_tags, action_status_message)
    """
    if not response_text:
        return ("", "")

    # Match all [ACTION:...] occurrences
    tag_matches = re.findall(r"\[ACTION:(.*?)\]", response_text, flags=re.IGNORECASE)

    # Clean the tags out of the response so the user sees clean text
    cleaned_text = re.sub(r"\[ACTION:.*?\]", "", response_text).strip()
    # Normalize multiple spaces left behind by tag removal
    cleaned_text = re.sub(r"\s{2,}", " ", cleaned_text)

    if not tag_matches:
        return (cleaned_text, "")

    action_messages = []

    for tag in tag_matches:
        action = tag.upper().strip()

        if action in ("OPEN_VSCODE", "OPEN_CODE"):
            status = launch_application("vscode")
        elif action in ("OPEN_TASK_MANAGER", "OPEN_TASKMGR"):
            status = launch_application("taskmgr")
        elif action in ("OPEN_CALCULATOR", "OPEN_CALC"):
            status = launch_application("calculator")
        elif action in ("OPEN_NOTEPAD",):
            status = launch_application("notepad")
        elif action in ("OPEN_BROWSER",):
            status = launch_application("browser")
        elif action in ("OPEN_SETTINGS",):
            status = launch_application("settings")
        elif action in ("CLEAN_WIN_TEMP", "CLEAN_TEMP", "CLEAN_JUNK"):
            # Run cleanup
            status = clean_windows_temp()
        elif action in ("CHECK_RAM", "CHECK_SYSTEM", "CHECK_CPU", "SYSTEM_STATS"):
            status = get_system_stats()
        elif action.startswith("OPEN_"):
            # Dynamic app launcher for any OPEN_<APP> tag
            target_app = action[5:].lower()
            status = launch_application(target_app)
        else:
            status = f"Unknown action: {action}"

        if status:
            action_messages.append(status)

    combined_status = " | ".join(action_messages)
    return (cleaned_text, combined_status)

