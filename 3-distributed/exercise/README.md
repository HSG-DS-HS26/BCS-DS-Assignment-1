# Task 3: MapReduce across a cluster

Tundrell Group, the parent company of Tundrell Outfitters from Task 1, keeps ten
million words of product descriptions and supplier correspondence. Management
wants a word count to compare the language used in each source.

Task 2 ran MapReduce on one machine. This task distributes it across one Master
and n Workers that share no memory and communicate through gRPC.

Points are awarded for the plain gRPC system. DISSALy adoption follows and is
required for Task 5.

Task 2 supplied the MapReduce infrastructure on one machine. This task supplies
cluster infrastructure. Design the messages and handlers that let the Master and
Workers coordinate. [`COMPONENTS_OF_MAPREDUCE.md`](COMPONENTS_OF_MAPREDUCE.md)
describes the supplied components and their constraints.

> **Windows.** The commands use a POSIX shell. Codespaces and the Dev Container
> provide one. PowerShell alternatives appear beneath each command block; see
> [Running the commands on Windows](../../README.md#running-the-commands-on-windows).

## Your files

| | |
|---|---|
| [`src/main/proto/worker.proto`](src/main/proto/worker.proto) | the protobuf specification  |
| [`src/main/java/mapreduce/Worker.java`](src/main/java/mapreduce/Worker.java) | the map handler and the reduce handler |
| [`src/main/java/mapreduce/Heartbeat.java`](src/main/java/mapreduce/Heartbeat.java) | the liveness handler |
| [`src/main/java/mapreduce/RunJob.java`](src/main/java/mapreduce/RunJob.java) | the infrastructure map reduce job (mostly given) |

## Fixed names

| service | rpcs |
|---|---|
| `mapreduce.Worker` | `RunMapTask`, `RunReduceTask`, and the supplied `Fetch` |
| `mapreduce.Heartbeat` | `Ping` |

Messages: `MapTask`, `MapDone`, `ReduceTask`, `ReduceDone`, `Beat`, `Alive`.

Task 5 simulations use these names.


## Procedure

### Read

[`COMPONENTS_OF_MAPREDUCE.md`](COMPONENTS_OF_MAPREDUCE.md), then
[`infrastructure.proto`](src/main/proto/infrastructure.proto), which holds the
`Master` service and the `Pair` both schemas share, then
[`infrastructure/Shuffle.java`](src/main/java/infrastructure/Shuffle.java), which
your `Worker` extends and which decides what your reduce handler has to do.
Then [`mapreduce/RunJob.java`](src/main/java/mapreduce/RunJob.java) and
[`infrastructure/JobContext.java`](src/main/java/infrastructure/JobContext.java).

### Design

Name the messages and their fields in words before writing any protobuf.
`worker.proto` asks what each one has to carry and why the receiver needs it.

Say what each one carries in words rather than type names: "one chunk of the
input", "the counts for reduce task 3 out of chunk 11", "are you still there".
That sentence is what Task 4 asks you to defend, and writing it first is what
stops a field from being in the message because it was easy to add.

### Build

This directory has its own Gradle build and uses real ports. Open
[`assignment.code-workspace`](../../assignment.code-workspace) to add this build
to the editor. Run **Compile Task 3** from the status bar to compile the code
and schema. See [Workspace setup](../README.md#workspace-setup).

```bash
./gradlew run
./gradlew run --args="20000 5 21 7 7"      # words seed M R workers
```

> **Windows (PowerShell).** On a Windows host:
>
> ```powershell
> .\gradlew.bat run
> .\gradlew.bat run '--args=20000 5 21 7 7'      # words seed M R workers
> ```

The plain-gRPC default is deliberately 20 000 words for fast feedback while you
test the handlers. Task 5 simulations use 10 000 000 words. The remaining
defaults are seed 5, M = 21, R = 7, and n = 7.

| | | after this stage |
|---|---|---|
| 1 | the messages, and the rpcs inside `service Worker` | the project builds, and every call returns `UNIMPLEMENTED` |
| 2 | [Heartbeat](COMPONENTS_OF_MAPREDUCE.md#heartbeat), which is independent of the rest | the Master can tell a dead Worker from a slow one |
| 3 | the map handler and `RunJob.mapRequest` | `3 rounds of map tasks`, and the reduce phase still fails |
| 4 | the reduce handler, `RunJob.reduceRequest`, and the marked merge in `RunJob.java` | **24 rows and a total of 20 000** |

The row count is easy to get right by accident. The total is not.

```bash
./gradlew partitionCheck
```

> **Windows (PowerShell).** On a Windows host:
>
> ```powershell
> .\gradlew.bat partitionCheck
> ```

`partitionCheck` verifies that partitioning assigns each word to one reduce
task. A wrong total points to a request or reduce-collection error.


## `JobContext`

`RunJob` receives job state through `JobContext`. Read the interface before
completing the request builders.

**Job facts.** Fixed for the lifetime of the job.

| | |
|---|---|
| `request()` | what the client submitted: words, seed, M, R, the Worker addresses |
| `mapTasks()`, `reduceTasks()` | M and R |
| `allWorkers()` | every address the job started with, failed ones included |

`allWorkers()` includes failed Workers; `live()` does not. The distinction
matters when machines have to agree on the holder of a map task's output.

**Current state.** Each method returns a snapshot.

| | |
|---|---|
| `live()` | the Workers not written off |
| `failed()` | the ones that were |
| `holders()` | for each map task, the Worker believed to be holding its intermediate pairs |

**Contacting a Worker.**

| | |
|---|---|
| `channel(worker)` | reusable channel; do not create one per call |
| `taskDeadlineMs()` | deadline for every task call |
| `probe(worker)` | sends the liveness rpc and reports whether the Worker replied; it changes no job state |
| `next(turn)` | next live Worker, round-robin |

**Recording results.**

| | |
|---|---|
| `mapTaskFinished(task, worker, firstRun)` | records that the task's intermediate pairs are on that Worker |
| `mapTaskFailed(worker, firstRun)`, `reduceTaskFailed(worker)` | update counters only |
| `reduceTaskFinished()` | records one completed reduce task |
| `countMapRound()`, `countReduceRound()` | once per *batch* assigned and awaited |

**State changes.**

| | |
|---|---|
| `strikeOff(worker)` | removes the Worker from `live()`, records it in the result, and stops future assignment |
| `remap()` | re-runs map tasks whose holder was struck off |
| `giveUp(attempts, task, what)` | stops retrying a task that no Worker accepts |

Only `strikeOff` marks a Worker as failed, and `RunJob` decides when to call it.

Each method is safe to call concurrently, but a sequence of calls is not atomic.
With several tasks in flight, `live()` may change before the next call.
