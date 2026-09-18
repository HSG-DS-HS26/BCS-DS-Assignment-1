#!/usr/bin/env bash
# Writes the PDF you upload to Canvas, after checking that GitHub has your work.
# Commit and push first. The work itself is in submission/Submission.java, which
# the Hand in button and submit.ps1 run too.
set -e
cd "$(dirname "$0")"
exec java submission/Submission.java "$@"
