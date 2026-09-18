#!/bin/sh
# Starts a Dissaly console and resolves its toolchain when its classpath is absent.
# Devcontainer lifecycle commands stop after a non-zero exit, so this script reports
# failures without preventing the remaining consoles from starting.
#
# Usage:  console.sh <lab-dir> <dissaly args…>
#   console.sh 1-spot serve --root 1-spot --port 19841
#   console.sh 1-spot serve docs --port 19844

cd "$(dirname "$0")/.." || exit 0

LAB="$1"
[ -n "$LAB" ] || { echo "console.sh: no lab directory given" >&2; exit 0; }
shift

CLASSPATH_FILE="$LAB/build/dissaly/classpath"

# `dissalyToolchain` writes this plain classpath file. The `.properties` variant
# escapes `:` and `\`, which makes it unsuitable as the Java classpath here.
if [ ! -s "$CLASSPATH_FILE" ]; then
    echo "resolving the simulator toolchain (first run or cleaned build directory)..."
    if ! ./gradlew -q dissalyToolchain; then
        echo
        echo "The simulator toolchain did not resolve, so this console will not start."
        echo "Run  ./gradlew dissalyToolchain  in a terminal once the network settles,"
        echo "then reload the window."
        exit 0
    fi
fi

if [ ! -s "$CLASSPATH_FILE" ]; then
    echo "$CLASSPATH_FILE is still missing after dissalyToolchain; nothing to start." >&2
    exit 0
fi

# Forces IPv4 so VS Code detects the console port in the Ports panel.
exec java -Djava.net.preferIPv4Stack=true -cp "$(cat "$CLASSPATH_FILE")" dissaly.cli.Main "$@"
