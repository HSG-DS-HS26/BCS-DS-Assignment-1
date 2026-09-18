# Writes the PDF you upload to Canvas, after checking that GitHub has your work.
# Commit and push first. The work itself is in submission/Submission.java, which
# the Hand in button and submit.sh run too.
#
# We recommend a Codespace or the Dev Container, where the Hand in button and
# ./submit.sh work as printed. If Windows refuses to run this script, type the
# line it runs instead, from the repository root:
#
#     java submission\Submission.java

Push-Location $PSScriptRoot
try {
    & java submission/Submission.java @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
