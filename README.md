# Assignment 1: Map Reduce 

Create one private repository for your pair using [SETUP.md](SETUP.md). Both
partners work and submit from that repository.

In your copy, choose `Code -> Codespaces -> Create codespace on main`.

No local installation or configuration is required.

Start with [TASKS.md](TASKS.md). This file describes the repository.


---

## Structure


```
TASKS.md                        overview over all tasks
submit.sh  submit.ps1           helper submit script
submission/                     helper tools for submission

1-spot/                     Task 1: 
  README.md                     technical guidance
  simulations/                  simulation specifications
  proto/  src/                  schemas and code
2-mapreduce/                Task 2: 
  src/interfaces/               interfaces
  src/engine/                   machinery code (given)
  src/implementations/          TODO for you
  src/jobs/                     helper
  src/Main.java                 main 
  input/                        inputs for different tasks
3-distributed/              
  README.md                     technical guidance
  tutorial/                     gRPC + DISSALy tutorial
    src/main/                   main folder
    .solution/plain/            gRPC solution
    .solution/dissaly/          DISSALy solution
    simulations/                simulation specifications
    README.md                   technical guidance
  exercise/                 Task 3:
    COMPONENTS_OF_MAPREDUCE.md  theory
    src/main/proto/             protobuf specifications
    src/main/java/infrastructure/
                                java code (given)
    src/main/java/mapreduce/    java code (TODO)
    simulations/                simulation specification
5-presentation/             Task 5:
  TASK5.md                      task
  README.md                     technical guidance
.viva/                      Task 4: the defense of your submission
```

The structure of the exercises is always the same, see [TASKS.md](TASKS.md) for the current exercise and the respective READMEs give more guidance on the technical details to solve the exercises.

## Co-Programming with Codespaces (experimental)

A Codespace belongs to one account. Your partner cannot open yours, so each of
you creates one from the shared repository and you exchange work through
`git push` and `git pull` as usual.

To sit in the same file at the same time, use **Live Share**. It is already
installed in the Codespace. One of you hosts:

1. `Ctrl/Cmd+Shift+P`, then **Live Share: Start Collaboration Session (Share)**,
   and sign in with GitHub.
2. Send your partner the link that lands on your clipboard.

The guest opens that link in a browser and joins. No second Codespace is
started, so it costs nothing against their monthly quota.

Three things to know before you start:

- The terminal is not shared until the host runs **Live Share: Share Terminal**
  and grants read/write access. Without it the guest can edit code but cannot
  run `./gradlew`.
- A console on port 19841 and the other ports reaches the guest only after
  **Live Share: Share Server**.
- The guest's edits are made in the host's Codespace, so only the host can
  commit them. Take turns hosting, or add your partner with a
  `Co-authored-by: Name <email>` line in the commit message. Both of you must
  appear in the history.

## Local development

**We recommend against working on your own machine directly.** Use a Codespace,
or the Dev Container below. Both give everyone the same Linux toolchain, the
same JDK and the same consoles, so a command that works for one of you works for
all of you, and what you hand in is what the TA runs. When running the code on your 
own machine you will end up with different versions and inconsistencies quite early,
it is just not worth the hassle.

Install [Docker Desktop](https://www.docker.com/products/docker-desktop/),
[Visual Studio Code](https://code.visualstudio.com/download), and the
[Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers).
Open the folder in VS Code and choose
[Reopen in Container](https://code.visualstudio.com/docs/devcontainers/containers#_reopen-a-folder-in-a-container).

Use Docker with VS Code when possible. On macOS, `./gradlew dissalyToolchain`
fetches a compatible protobuf compiler. The committed Linux binaries do not run
there, so the schema can also be compiled outside a container. The container
provides the Linux toolchain and a fixed JDK version.

## Running the commands on Windows

Every command printed in this repository is written for a POSIX shell. In a
Codespace or the Dev Container that is the shell you get, whatever your laptop
runs, so the commands work as printed and this section is not for you. That is
the route we recommend; nonetheless, if you still want to go for windows, here you are.

Every command block in these documents has a **Windows (PowerShell)** box beneath
it with the same commands. VS Code's **Run Task** entries and the **Hand in**
button pick the Windows command by themselves. This section explains the
translation those boxes use.

| printed as | in PowerShell |
|---|---|
| `./gradlew <task>` | `.\gradlew.bat <task>` |
| `curl -sSLo dissaly.jar <url>` | `curl.exe -sSLo dissaly.jar <url>`, so the flags stay as they are |
| `rm -f <file>`, `rm -rf <dir>` | `Remove-Item -Force -ErrorAction Ignore <file>`, and `-Recurse` for a directory |
| `diff -ru <a> <b>` | `git diff --no-index <a> <b>` |
| `./submit.sh` | `.\submit.ps1` |
| `./dissaly <args>` | three lines, below |
| `git …`, `gh …`, `java …` | unchanged |

`dissaly adopt` writes a `sh` launcher and no `.bat` beside it, so after
adoption `./dissaly build` followed by `./dissaly check` is:

```powershell
.\gradlew.bat -q dissalyToolchain
$cp = (Get-Content build\dissaly\classpath -Raw).Trim()
java -cp $cp dissaly.cli.Main build
java -cp $cp dissaly.cli.Main check
```

That is what the launcher does: it resolves the classpath into
`build/dissaly/classpath` and runs `dissaly.cli.Main` against it. The file is
written with the separator the platform uses, so it is already `;` on Windows.
The launcher repeats the first two lines before every command; in PowerShell,
once per terminal is enough unless a build file changes, and after that each
`./dissaly` command is one `java -cp $cp dissaly.cli.Main` line.

If Windows refuses to run `.ps1` scripts such as `submit.ps1`, type the command
the script runs instead; `submit.ps1` says which at the top.


### AI Usage Disclaimer
LLMs were used in the preparation of this assignment template to support the development process, for example, for code scaffolding and layout. The learning objectives, structure, and content of the assignment, including all student-facing templates and materials, were designed by the TAs and the coordinating professor. Any LLM-generated code was reviewed and tested before release.
