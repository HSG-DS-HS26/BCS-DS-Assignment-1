# Tutorial: one gRPC system before and after

> Required, but not graded.

A thumbnail is a small preview of an image. A thumbnail service receives an
image, makes its preview, and keeps it until the client asks for it.

In this tutorial you will write a plain gRPC program, then adopt it with `dissaly adopt`, and
run the same program on a simulated cluster.

| stage | work | result |
|---|---|---|
| 1 | implement the program with gRPC | locally running gRPC program |
| 2 | run `dissaly adopt` on the project | the same program, adopted for the simulator |
| 3 | make the edits adoption leaves to you | a program that runs on simulated nodes |
| 4 | run the four simulations | observable system reactions |

> **On Windows.** The commands below assume a POSIX shell, which is what a
> Codespace and the Dev Container give you, and that is the recommended way to
> work. On a Windows host, each command block has its PowerShell version beneath
> it; [Running the commands on Windows](../../README.md#running-the-commands-on-windows)
> explains the translation.

## What is in this folder

| | |
|---|---|
| `src/main/` | scratch folder with the stage 1 files that stage 2 adopts |
| `.solution/plain/` | those three files finished, as they are at the end of stage 1 |
| `.solution/dissaly/` | the stage 3 result after adoption |
| `simulations/` | supplied scenarios; adoption writes the first scenario |

Read and compare the solutions. Copy from them if needed.

`src/main/` keeps Gradle's name because adoption moves `src/main/proto/` to
`proto/` and `src/main/java/` to `src/`.

> After adoption, `dissaly check` reads each `.java` file it finds. It skips
> dot-directories, so it ignores the reference copies that still open sockets and
> include `main`.

> Type stage 1 yourself, to practise using a `StreamObserver`. Stages 2 and 3
> are mechanical, nothing in them is graded, and we recommend handing them to an
> AI agent: point it at the steps below and at `.solution/dissaly/`, then read
> the diff it produces. What matters is the reason for each edit, which every
> step states, not the typing.
>
> Task 3 has different rules. Ask about the simulator, but design the system
> yourself. Task 4 requires you to defend it. See [`AGENTS.md`](../../AGENTS.md).

## Service

The service receives images, creates thumbnails, and keeps the thumbnails in
memory until collection.

~~~
Thumbnail(Image) -> Thumb     shrink one image and keep the result
Collect(Empty)   -> Album     return everything made by this node
~~~

The `album` field in the handler holds the thumbnails. Stage 4 measures that
retained memory, and it is the reason the projection to forty million images has
anything interesting to say.

## Stage 1: the plain gRPC program

Type the empty files below. The finished versions are in `.solution/plain/`.

| file | role |
|---|---|
| `src/main/proto/thumbs.proto` | service schema |
| `src/main/java/thumbs/ThumbServer.java` | server and handler |
| `src/main/java/thumbs/ThumbClient.java` | client |

Read each finished file, then type your version. This gives you practice with a
`.proto` file and `StreamObserver` before Task 3.

### Schema

Use `proto3`, package `thumbs`, `java_package = "thumbs"`, and
`java_multiple_files = true`. The last option gives each message its own Java
class, so the code uses `Image` rather than `Thumbs.Image`.

The service has these messages:

| message | fields |
|---|---|
| `Empty` | none; `Collect` takes no argument |
| `Image` | `string id`, `bytes pixels` |
| `Thumb` | `string id`, `int32 bytes`, `string from` |
| `Album` | `string from`, `repeated Thumb thumbs`, `int64 total_bytes` |

Set `idempotency_level` to `IDEMPOTENT` for `Thumbnail` and `NO_SIDE_EFFECTS`
for `Collect`. Stage 1 does not use these options. Stage 4 uses them when it
decides whether a call can be retried.

### Generated Java

Build from this directory:

~~~bash
./gradlew build
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat build
> ~~~

Generated classes go under `build/generated/sources/proto/main/`. Inspect
`ShrinkerGrpc.ShrinkerImplBase`, `ShrinkerGrpc.newBlockingStub(channel)`, and
`Thumb.java` with `newBuilder()`. The server extends the base class, the client
uses the blocking stub, and builders create immutable messages.

Do not edit or commit `build/`. Change the schema and build again.

### Server

The server follows the [gRPC Java quickstart](https://grpc.io/docs/languages/java/quickstart/).
`ThumbServer` binds a port and waits for termination:

~~~java
server = ServerBuilder.forPort(PORT).addService(new Shrinker()).build().start();
...
server.awaitTermination();
~~~

`Shrinker` extends `ShrinkerGrpc.ShrinkerImplBase` and implements both RPCs.
Each handler sends a reply with `out.onNext(reply)` and closes the call with
`out.onCompleted()`.

`thumbnail` reads the image bytes, creates a `Thumb` of `pixels.length / 64`
bytes, stores it in the album, and returns it. `collect` returns the album and
its `total_bytes`.

### Client

The client connects to `localhost:50052`:

~~~java
ManagedChannel channel = Grpc.newChannelBuilder("localhost:50052",
        InsecureChannelCredentials.create()).build();
var stub = ShrinkerGrpc.newBlockingStub(channel);
~~~

Send 40 images of 64 kB, using one buffer from `new Random(1)`. Call `collect`
once, print the response, and close the channel in a `finally` block.

### Run

Open two terminals at the same time:

~~~bash
./gradlew run
./gradlew client
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat run
> .\gradlew.bat client
> ~~~

The client should report:

~~~text
40 images -> 40 thumbnails from localhost:50052, 40960 bytes
~~~

Each thumbnail has 1,024 bytes because 64 kB divided by 64 is 1,024.

### Compare with the finished files

~~~bash
diff -ru .solution/plain/proto src/main/proto
diff -ru .solution/plain/java  src/main/java
~~~

The finished files show one valid implementation. Check schema differences
carefully, because the later stages depend on the schema. Also please note that
`.solution/` is not on the build path; Gradle compiles `src/main/` only.

## Limits of the plain program

The plain program measures one node. It cannot show the speed and cost of a
four-node cluster, the thumbnails lost when a node dies halfway through, or the
memory required for forty million images.

Starting four servers on one laptop would measure the laptop rather than a
cluster. Killing a process by hand would not model a simulated node failure.
Forty million 64 kB images would require about 2.5 TB. For such simulation we
need to use DISSALy.

## Stage 2: adoption

Your program binds a real port and dials a real address, and a simulated cluster
has neither. Adoption rearranges the project into the layout dissaly builds
from; your Java changes in stage 3.

> Worth giving to an agent: steps 1 to 3 are three commands and a report to
> read. Ask it to commit, run the adoption, and show you what changed.

### Step 1: commit stage 1

Adoption refuses a dirty tree, and it moves your files with `git mv`, so from a
committed stage 1 everything it does is a diff you can read and undo. Commit
first:

~~~bash
git status --porcelain   # prints nothing when the tree is clean
git add -A
git commit -m "tutorial stage 1"
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> git status --porcelain   # prints nothing when the tree is clean
> git add -A
> git commit -m "tutorial stage 1"
> ~~~

### Step 2: run the adoption

Then fetch the tool and run it from this directory:

~~~bash
curl -sSLo dissaly.jar https://github.com/Interactions-HSG/losim/releases/latest/download/dissaly.jar
java -jar dissaly.jar adopt .
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> curl.exe -sSLo dissaly.jar https://github.com/Interactions-HSG/losim/releases/latest/download/dissaly.jar
> java -jar dissaly.jar adopt .
> ~~~

| before | after | reason |
|---|---|---|
| `src/main/java/thumbs/` | `src/thumbs/` | dissaly compiles from `src/`, not from Gradle's layout |
| `src/main/proto/` | `proto/` | the schema is generated by dissaly's own protobuf step |
| `build.gradle` | `build.gradle.kts`, `build.gradle.bak` | the new build adds the dissaly runtime and the `dissaly.Job` stubs; the `.bak` copy is your old build and can be deleted |
| | `./dissaly` | a launcher pinned to one version, instead of `java -jar` |
| | `AGENTS.md` | notes for an AI agent working in the adopted project |
| | `simulations/1-one-call.yaml` | the smallest scenario, so there is something to run |

Your Java files are untouched. `./gradlew run` and `./gradlew client` are not
needed any more: a program starts because a scenario names it.

### Step 3: read the check report

`check` reports what cannot work inside a simulation, and the program is still
written for one laptop, so expect findings: they name the two files you rewrite
in stage 3.

~~~bash
./dissaly build
./dissaly check
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main build
> java -cp $cp dissaly.cli.Main check
> ~~~

| status | meaning |
|---|---|
| refused | the program cannot run; the report includes a line number |
| untrustworthy | the program runs but produces a wrong number |
| dead | the program runs without producing a result |

## Stage 3: the source changes

Adoption moved your files; it did not change your program, and the check from
step 3 lists what is left. In a simulation nobody starts a `main`: dissaly
starts the nodes, and each scenario says which class a node runs.

~~~yaml
nodes:
  master: { runs: { dissaly.Job: src/thumbs/ThumbJob.java } }
  w:      { runs: { Shrinker:    src/thumbs/Shrinker.java } }
~~~

So your client becomes a `dissaly.Job` with two calls, `Load` before measurement
and `Run` inside it, and your server becomes the handler class on its own. The
steps below make that change: the schema first, because both classes need it,
then the two classes, then the cost of a call.

> Worth giving to an agent as well: steps 4 to 7 are a known edit with a known
> answer. Give it this section and `.solution/dissaly/`, ask it to work until
> `./dissaly check` is clean, then read the diff step by step against the
> reasons given here. It is your job to be able to say why each edit was
> needed, which is what step 8 checks.

### Step 4: add `Split` to the schema

`Load` hands its result to `Run` inside a dissaly `Workload`, which carries a
type name and bytes, so that result has to be a protobuf message. It needs the
number of images, and one pixel buffer that every image reuses, so that runs of
1,000 and of 8,000 images are made of the same picture.

Add it to `proto/thumbs.proto`, then run `./dissaly build` so the generated
`Split` class exists:

~~~proto
// `Job.Load` builds this value and `Job.Run` processes it. dissaly's `Workload`
// carries a type name and bytes for this job.
message Split {
  int32 images = 1;
  // Pixels for one image, reused for each item to keep image content fixed
  // during cluster measurements.
  bytes pixels = 2;
}
~~~

### Step 5: move the handler into its own file

dissaly constructs one handler per node from the `runs:` line, which works only
for a top-level class that needs no constructor arguments. The rest of
`ThumbServer` has nothing to bind to in a simulation, and a leftover `main` is
worse than dead code: a run that names no class starts the first `main` it
finds.

Take `Shrinker` out of `ThumbServer`, put it in `src/thumbs/Shrinker.java` as a
top-level `public final class` with no constructor, and delete
`ThumbServer.java`. Then make three edits inside the handler:

- `Thumb.from` reports `Dissaly.current().node()` instead of
  `"localhost:" + PORT`, since the answer now differs per node.
- `here.units(Math.max(1, pixels.length / 1024))` reports the call's size in
  kilobytes. `perUnit` is multiplied by it and stage 4 fits its laws against
  units, so without it every image costs the same and the projection has nothing
  to scale with.
- `here.reveal("album", album.size() + " thumbnails")` puts the retained count
  on the node in the trace, so you watch the album grow during the run.

The album, the loop over the pixels and the `/64` arithmetic stay as they were:
the simulation measures the work the handler does, so removing work changes the
numbers.

### Step 6: turn the client into a job

dissaly creates the channels and decides when measurement begins, so the client
keeps only what it is for, preparing the images and sending them.
`ManagedChannel`, `Grpc.newChannelBuilder` and `localhost:50052` go; a
hand-built channel that survives the rewrite fails on its first call with *"No
functional channel service provider found"*.

Replace `ThumbClient` with `src/thumbs/ThumbJob.java`:

~~~java
public final class ThumbJob extends JobGrpc.JobImplBase {
    @Override public void load(Input in, StreamObserver<Workload> out) { ... }
    @Override public void run(Workload work, StreamObserver<Result> out) { ... }
}
~~~

`load` builds the pixels before the clock starts:

~~~java
byte[] buf = new byte[PIXEL_BYTES];
new Random(Dissaly.current().seed()).nextBytes(buf);
~~~

The seed comes from the scenario, so the same file produces the same images
every time, and `in.getCount()` is the `count:` under `input:`. `Load` runs
outside the measured interval, so generating 64 kB of pixels is not counted as
work the cluster did.

`run` unpacks the `Split`, asks which nodes serve the handler, and opens one
stub per node:

~~~java
List<String> peers = here.peersServing("Shrinker");
var stub = ShrinkerGrpc.newBlockingStub(here.channelTo(peer));
~~~

`peersServing` returns the nodes whose `runs:` names that service, which is why
the same job works on a two-node and a four-node scenario unchanged. It then
sends the images round-robin, gives every call a deadline with
`withDeadlineAfter`, stops calling a node after three failures, and collects
from every node still answering. Stage 4 needs both: without the deadline the
job waits out the full freeze of a frozen node, without the strike count it
keeps sending images to a killed one.

What it reports goes into a `Result`:

~~~java
out.onNext(Result.newBuilder().putAnswer("thumbnails", ...).build());
~~~

The console shows that map when the run finishes, so any number you want to
compare between scenarios, such as thumbnails lost, belongs in it.

### Step 7: give the calls a cost

Adoption gives every RPC `fixed: 0`, which models no latency, no queueing, no
contention and no cost, so the run finishes at once and every scenario looks
alike. It also guesses the workload, `unit: item` and `count: 100`, and the
other three scenarios send forty images. Edit `simulations/1-one-call.yaml`
until it reads:

~~~yaml
seed: 1

nodes:
  master:
    instance: m5.large
    zone: eu-central-1a
    runs: { dissaly.Job: src/thumbs/ThumbJob.java }

  worker:
    instance: c5.large
    zone: eu-central-1a
    runs:
      Shrinker: src/thumbs/Shrinker.java

input:
  unit:  image
  count: 40

simulatedDuration:
  src/thumbs/Shrinker.java:
    Thumbnail: { fixed: 0.4 refMs, perUnit: 55000 refNs }
    Collect:   { fixed: 1 refMs }
~~~

`seed: 1` is what `Dissaly.current().seed()` returns in `Load`, so the same
images come back every run. `runs:` is the line from the start of this stage,
and it is why the two classes exist. `count: 40` is `in.getCount()` in `Load`
and `unit: image` names one of them, the same unit `units()` counts and
`perUnit` prices. Under `simulatedDuration`, the key is the handler file named
by `runs:`, `fixed` is the base cost of one call, and `perUnit` is added per
kilobyte: a 64 kB image costs 0.4 refMs + 64 x 0.055 refMs, about 3.9 refMs.
Scenarios 2, 3 and 4 already carry this block.

### Step 8: check the work

This step is yours whether or not an agent made the edits.

~~~bash
./dissaly build
./dissaly check
git diff
diff -ru .solution/dissaly/proto  proto
diff -ru .solution/dissaly/thumbs src/thumbs
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main build
> java -cp $cp dissaly.cli.Main check
> git diff
> git diff --no-index .solution/dissaly/proto  proto
> git diff --no-index .solution/dissaly/thumbs src/thumbs
> ~~~

Remaining findings name a file and a line. In `git diff`, the messages, the
generated code, the handler bodies and the album read as they did in stage 1:
what changed is transport and measurement, and steps 4 to 7 say why for each
change you see. Anything in the diff you cannot account for is either a
misunderstanding or an edit nobody asked for; both are worth chasing before
stage 4, where these files are what the numbers are measured from.

## Stage 4: the simulations

Run the scenarios. Adoption created `simulations/1-one-call.yaml`, which defines
the smallest system. The remaining scenarios are in
[`simulations/`](simulations/), and each changes the cluster configuration while
leaving the Java unchanged.

Start the console in this directory on port 19842:

~~~bash
./dissaly serve --port 19842
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main serve --port 19842
> ~~~

Run a simulation with:

~~~bash
./dissaly build
./dissaly simulate simulations/2-a-system.yaml --cp build/dissaly/classes
~~~

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ~~~powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main build
> java -cp $cp dissaly.cli.Main simulate simulations\2-a-system.yaml --cp build\dissaly\classes
> ~~~

The scenarios examine the same program under different conditions. Read the
comments in each YAML file while you inspect the run.

### `1-one-call.yaml`: the same system with an observable clock

This is the stage 1 laptop run with simulated durations and costs. It can answer
questions the plain program could not: how long does the worker take to process
the workload, and what does a `Thumbnail` call cost?

During the run, the job sends the workload to `worker` and collects the results
at the end. Watch the album count on that node while the film plays. Then open
the trace for a `Thumbnail` call.

You completed this file in step 7. Every later scenario changes the cluster
around the same classes:

~~~yaml
seed: 1

nodes:
  master:
    instance: m5.large
    zone: eu-central-1a
    runs: { dissaly.Job: src/thumbs/ThumbJob.java }

  worker:
    instance: c5.large
    zone: eu-central-1a
    runs:
      Shrinker: src/thumbs/Shrinker.java

input:
  unit:  image
  count: 40

simulatedDuration:
  src/thumbs/Shrinker.java:
    Thumbnail: { fixed: 0.4 refMs, perUnit: 55000 refNs }
    Collect:   { fixed: 1 refMs }
~~~

The master and worker are in `eu-central-1a`, so every call remains within the
zone. `peersServing("Shrinker")` returns the worker name, and every image goes
to that node.

### `2-a-system.yaml`: distribute the work

Workers are distributed across the configured availability zones so that the
service can survive a data centre failure. The clock and bill reflect that
arrangement. Cross-zone calls take longer, and bytes crossing the boundary incur
egress charges.

Open **Usage > Memory** and compare it with the earlier run. Thumbnails remain
on the node that created them, so memory is distributed across the workers
rather than concentrated on one node. Open **Cost** and locate the egress line
that was absent from the single-zone run. Then compare the makespan with
`1-one-call.yaml`. Adding workers does not make the job faster because it still
sends an image, waits for its reply, then sends the next. The extra nodes
distribute thumbnails but do not reduce that waiting. Task 3 addresses
overlapping those calls.

~~~yaml
seed: 1

nodes:
  master:
    instance: m5.large
    zone: eu-central-1a
    runs: { dissaly.Job: src/thumbs/ThumbJob.java }

  w:
    count: 4
    prefix: w
    instance: c5.large
    zone: [eu-central-1a, eu-central-1a, eu-central-1b, eu-central-1b]
    runs: { Shrinker: src/thumbs/Shrinker.java }

network:
  sameZone: 0.2 refMs
  crossZone: 0.8 refMs
  jitter: 0.1 refMs

input:
  unit:  image
  count: 40

simulatedDuration:
  src/thumbs/Shrinker.java:
    Thumbnail: { fixed: 0.4 refMs, perUnit: 55000 refNs }
    Collect:   { fixed: 1 refMs }
~~~

The `w` entry creates the named workers, and `peersServing("Shrinker")` returns
their names for the round-robin loop in `ThumbJob`. The `zone:` list determines
which workers run in `eu-central-1b`; the master remains in `1a`. Calls to those
workers use `crossZone: 0.8 refMs` rather than
`sameZone: 0.2 refMs`, plus `jitter`. Their transferred bytes appear as egress
under **Cost**. `input:` and `simulatedDuration:` remain unchanged from
`1-one-call.yaml`, leaving the cluster as the only variable.

### `3-straggler.yaml`: when some nodes cause problems

A machine may die, hang, or remain alive while becoming unreachable. The caller
sees a call that never returns, although these are different events. A partition
concerns a *pair* of nodes, so `w3` remains healthy and can answer other nodes
while the master cannot reach it.

Watch the run to see the cost of each failure. When `w1` is killed at 300 refMs,
the thumbnails held only by that worker disappear because its album stays in
memory until someone calls `collect`. A design that returned each thumbnail when
it was made would lose fewer results. When `w2` freezes at 500 refMs, the
deadline in `ThumbJob` ends the wait. The strike counter determines whether the
node is removed permanently even after it returns. Compare the `lost` and
`perNode` results with those from the earlier scenario.

~~~yaml
seed: 1

nodes:
  master:
    instance: m5.large
    zone: eu-central-1a
    runs: { dissaly.Job: src/thumbs/ThumbJob.java }

  w:
    count: 4
    prefix: w
    instance: c5.large
    zone: [eu-central-1a, eu-central-1a, eu-central-1b, eu-central-1b]
    runs: { Shrinker: src/thumbs/Shrinker.java }
    overrides:
      w1:
        failures:
          - { kill: true, at: 300 refMs }
      w2:
        failures:
          - { freeze: true, at: 500 refMs, for: 600 refMs }
      w3:
        failures:
          - { partition: master, at: 900 refMs }
          - { heal: master, at: 1600 refMs }

network:
  sameZone: 0.2 refMs
  crossZone: 0.8 refMs
  jitter: 0.1 refMs

retries:
  - { method: Thumbnail, attempts: 3, backoff: 40 refMs, multiplier: 2 }

input:
  unit:  image
  count: 40

simulatedDuration:
  src/thumbs/Shrinker.java:
    Thumbnail: { fixed: 0.4 refMs, perUnit: 55000 refNs }
    Collect:   { fixed: 1 refMs }
~~~

The cluster matches `2-a-system.yaml`, with `overrides:` added to the affected
nodes. `partition: master` identifies the other node in the pair, making this a
partition rather than a node death; the following entry heals the partition at
1600 refMs. `freeze` requires `for:` because the node returns. `kill` does not.
The `retries:` block configures the `Thumbnail` retry policy, starting with a 40
refMs backoff that doubles after each attempt. The engine permits this only because
`thumbs.proto` declares the method `IDEMPOTENT`. If that schema option is
removed, the scenario is rejected before it starts.

### `4-scaled.yaml`: a system larger than your machine

The configured workload requires about 2.5 TB across the worker nodes. A laptop
or codespaces cannot run it directly, but this file can still answer questions about it.
Dissaly runs a smaller workload repeatedly, measures resource use, and projects
those measurements to the full workload size. This is one of the purposes of the
simulation engine.

~~~yaml
seed: 1
scale: 5000

nodes:
  master:
    instance: m5.large
    zone: eu-central-1a
    runs: { dissaly.Job: src/thumbs/ThumbJob.java }

  w:
    count: 4
    prefix: w
    instance: c5.large
    zone: [eu-central-1a, eu-central-1a, eu-central-1b, eu-central-1b]
    runs: { Shrinker: src/thumbs/Shrinker.java }

    overrides:
      w1:
        failures:
          - { kill: true, at: 400 refMs }

network:
  sameZone: 0.2 refMs
  crossZone: 0.8 refMs
  jitter: 0.1 refMs

input:
  unit:  image
  count: 40000000

simulatedDuration:
  src/thumbs/Shrinker.java:
    Thumbnail: { fixed: 0.4 refMs, perUnit: 55000 refNs }
    Collect:   { fixed: 1 refMs }
~~~

The cluster and costs match the earlier scenarios. `scale` limits the work that
dissaly runs directly while `count` defines the full workload. Without `scale`,
the scenario would attempt the complete workload. With it, dissaly measures
smaller runs and reports estimates for the full workload. The run prints a line
for each resource. Resources it measured rather than counted include an error
band; [scale in the manual](http://localhost:19844/ref/scale) explains how to
interpret those results.

This file also uses the stage 3 edits. `Load` creates the images for smaller
runs, `Run` follows the same execution path at every size, and `units()` tells
the engine how much work a call performed.

## Manual

The [simulator manual](http://localhost:19844/) matches the pinned version:

| page | topic |
|---|---|
| [adopting a gRPC system](http://localhost:19844/start/adopt) | migration |
| [simulation file](http://localhost:19844/ref/simulation) | top-level keys |
| [`simulatedDuration`](http://localhost:19844/ref/simulated-duration) | call cost |
| [scale](http://localhost:19844/ref/scale) | projections |
| [failures](http://localhost:19844/ref/failures) | failure events |
| [retries](http://localhost:19844/ref/retries) | retry policy |
| [nodes](http://localhost:19844/ref/nodes), [network](http://localhost:19844/ref/network), [prices](http://localhost:19844/ref/prices) | resources and cost |
| [`dissaly.Job`](http://localhost:19844/ref/dissaly-proto), [Dissaly](http://localhost:19844/ref/api-dissaly) | schema and API |
| [input](http://localhost:19844/ref/input) | workload input |
| [trust rules](http://localhost:19844/ref/trust-rules) | untrustworthy results |
| [refusals](http://localhost:19844/simulate/refusals) | rejected simulations |
