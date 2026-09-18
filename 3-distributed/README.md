# 3-distributed: gRPC tutorial and MapReduce exercise

The tutorial is strongly recommended before the exercise.

| | | |
|---|---|---|
| [`tutorial/`](tutorial/) | TUTORIAL, 0 pt | Thumbnail service, then adoption |
| [`exercise/`](exercise/) | EXERCISE, Task 3, 4 pt | MapReduce with one Master and seven Workers |

The tutorial is not graded. It introduces `dissaly`, which rewrites the
build and moves your files.

Each half has its own Gradle build, `settings.gradle`, and `gradlew`. The root
build skips directories with `settings.gradle`. `dissaly adopt` requires this
single-project structure.

## Workspace setup

Open [`assignment.code-workspace`](../assignment.code-workspace). VS Code offers
it in a notification when you open the repository folder, in a Codespace, in the
Dev Container and locally alike; answer **Open Workspace**. If you dismissed it:
File -> Open Workspace from File.

The workspace imports the tutorial and exercise builds. Opening only the
repository folder leaves `3-distributed/` outside the editor's Gradle build, so
the editor cannot report its Java errors.

Run **Compile Task 3** from the status bar or Terminal -> Run Task. These Gradle
tasks compile the same sources as the assignment stages:

```bash
./gradlew task3Compile        # or tutorialCompile
```

> **Windows (PowerShell).**
>
> ```powershell
> .\gradlew.bat task3Compile
> ```

Run the task after changes. The Task 3 build requirement is checked by Gradle.

## tutorial/

This project moves from a plain gRPC program on a real socket to a simulated
cluster. Follow the stages in [`tutorial/README.md`](tutorial/README.md).

| | |
|---|---|
| `src/main/` | schema, server, and client |
| `.solution/plain/` | Completed files before adoption |
| `.solution/dissaly/` | Completed files after adoption: handler and client job |
| `simulations/` | Supplied scenarios. Adoption adds another scenario |
| `README.md` | the walkthrough |

`src/main/` keeps Gradle's name because `dissaly adopt` moves
`src/main/proto/` to `proto/` and `src/main/java/` to `src/`.

`.solution/` starts with a dot because `dissaly check` skips dot-directories.
The reference copies retain sockets and `main` methods that do not belong in the
adopted simulation.

## exercise/

Task 3 is the same as Task 2's word count, but this time across machines with no shared memory. The
machines communicate through gRPC. Follow [`exercise/README.md`](exercise/README.md).

| | |
|---|---|
| `COMPONENTS_OF_MAPREDUCE.md` | Every component of a MapReduce and potential pitfalls to address |
| `src/main/proto/infrastructure.proto` | `Master` service (given) |
| `src/main/proto/worker.proto` | Some parts are already given, but still some TODOs left for you |
| `src/main/java/infrastructure/` | Master, job state, corpus, shuffle, and user programs (given) |
| `src/main/java/mapreduce/` | your code to implement |
| `src/main/java/checks/` | `./gradlew partitionCheck` (given) |
| `simulations/` | The simulation scenarios in DISSALy (given) |

## The consoles

Both exercises (tutorial and map reduce) start as a plain gRPC program for you to implement, followed by an adoption to DISSALy.

| port | what | before adoption |
|---|---|---|
| [19842](http://localhost:19842/) | the tutorial | `./gradlew tutorialServer` and `tutorialClient`, over port 50052 |
| [19843](http://localhost:19843/) | the exercise | `./gradlew task3`, which starts the machines on 50050 upwards, runs one job and exits |
| [19844](http://localhost:19844/) | the simulator manual | always up |

All of those are also under **Terminal -> Run Task**.

> **On Windows.** Use a Codespace or the Dev Container, which is the recommended
> way to work: the consoles come up with it and every command here runs as
> printed. On a Windows host, **Run Task** picks the Windows command by itself,
> and [Running the commands on Windows](../README.md#running-the-commands-on-windows)
> explains how the rest translate.
