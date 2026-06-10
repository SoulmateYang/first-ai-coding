#!/usr/bin/env bash
# FirstCode Unix launcher (macOS / Linux).
#
# Usage:
#   ./firstcode.sh         direct start
#   ./firstcode.sh --help  show args
#
# Requires: this script must be run from the project root, and
# target/firstcode-0.1.0-all.jar must already exist (run `mvn package` first).

set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR="${SCRIPT_DIR}/target/firstcode-0.1.0-all.jar"

if [[ ! -f "${JAR}" ]]; then
    echo "ERROR: fat-jar not found at ${JAR}" >&2
    echo "Run \`mvn package\` first." >&2
    exit 1
fi

# Explicit UTF-8, avoid macOS default LANG triggering Chinese mojibake
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

exec java -Dfile.encoding=UTF-8 -jar "${JAR}" "$@"
