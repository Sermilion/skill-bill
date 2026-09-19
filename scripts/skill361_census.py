#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import skill361_nest  # noqa: E402

if __name__ == "__main__":
    violations = skill361_nest.census_violations()
    print(f"violations={len(violations)}")
