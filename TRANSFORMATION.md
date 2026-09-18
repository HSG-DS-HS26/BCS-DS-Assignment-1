# Turning the Task 3 cluster into a simulation

This is what happens between a working `./gradlew run` and a first
`./dissaly simulate`. It is mechanical, none of it is graded, and an agent may do
all of it: see the "Running `dissaly adopt`" section of
[AGENTS.md](AGENTS.md) for where that line falls and what stays the
student's.

Do it only once [`3-distributed/exercise`](3-distributed/exercise/) prints
**24 rows and a total of 20 000**. Adoption does not touch a single `.java`
file, so it cannot fix a system that is wrong; it only changes where the files
sit and who provides the network.

> **On Windows.** Every command here assumes a POSIX shell, which is what a
> Codespace and the Dev Container give you, and that is the recommended way to do
> this. On a Windows host, each command block has its PowerShell version beneath
> it; [Running the commands on Windows](README.md#running-the-commands-on-windows)
> explains the translation.

---

## 1. Get the jar

There is no `dissaly.jar` in the tree. dissaly is resolved by coordinate, and the
normal path is to download one:

```bash
curl -sSLo dissaly.jar https://github.com/Interactions-HSG/losim/releases/latest/download/dissaly.jar
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> curl.exe -sSLo dissaly.jar https://github.com/Interactions-HSG/losim/releases/latest/download/dissaly.jar
> ```

That fetches the **latest** release, which need not be the version the build
pins. Offline, or when the versions must match, use the jar the build already
resolved:

```bash
# from the repository root
JAR=$(tr ':' '\n' < 1-spot/build/dissaly/classpath | grep -E 'dissaly-[0-9].*\.jar' | head -1)
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> # from the repository root
> $JAR = (Get-Content 1-spot\build\dissaly\classpath -Raw).Trim().Split([IO.Path]::PathSeparator) |
>     Where-Object { $_ -match 'dissaly-[0-9].*\.jar$' } | Select-Object -First 1
> ```

If `1-spot/build/dissaly/classpath` is missing, run `./gradlew dissalyToolchain`
once and it reappears.

## 2. Commit, then adopt

Adoption refuses a dirty tree, and against a clean one its `git mv`s are a diff
you can read and revert. Do not pass `--dirty`.

```bash
cd 3-distributed/exercise
git -C ../.. status --porcelain      # must be empty
java -jar "$JAR" adopt .
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> cd 3-distributed\exercise
> git -C ..\.. status --porcelain      # must be empty
> java -jar $JAR adopt .
> ```

**What it changes:**

| before | after |
|---|---|
| `src/main/java/<pkg>/` | `src/<pkg>/` |
| `src/main/proto/` | `proto/` |
| `build.gradle` | `build.gradle.kts`, plus a `build.gradle.bak` worth deleting |
| | a `./dissaly` launcher, a generated `AGENTS.md`, and one starter scenario |

`simulations/` survives untouched. Adoption adds `1-one-call.yaml` beside the
nine scenarios that were already there and leaves them alone.

---

## 3. Delete the halves that only made sense with sockets

```bash
rm -f build.gradle.bak
rm -f src/infrastructure/GrpcUserProgram.java src/infrastructure/GrpcMaster.java
rm -rf src/checks
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> Remove-Item -Force -ErrorAction Ignore build.gradle.bak
> Remove-Item -Force -ErrorAction Ignore src\infrastructure\GrpcUserProgram.java, src\infrastructure\GrpcMaster.java
> Remove-Item -Recurse -Force -ErrorAction Ignore src\checks
> ```

`GrpcUserProgram` binds real ports and `GrpcMaster` dials them, and `dissaly
check` refuses both inside a lab. `src/checks/PartitionCheck.java` has done its
job by now and carries a `main`, which is worse than dead: with no simulation
named, dissaly runs the first class it finds with one.

`DissalyUserProgram` and `DissalyMaster` are their finished counterparts and are
already in the tree.

## 4. The edits in `src/infrastructure/Shuffle.java`

Nothing else in the project needs changing, and nothing will remind you about
them. They are marked `ADOPTION EDIT` in the file.

**The channel.** A simulated node has no sockets, so a peer is found by what it
serves:

```java
protected final Channel channel(String address) {
    return Dissaly.current().channelTo(address);
}
```

Delete the `channels` field above it and the `io.grpc.ManagedChannel` import,
and add `import dissaly.api.Dissaly;`. Left as it was, the first shuffle read
fails with *"No functional channel service provider found"* and the run ends
having done no work. `./dissaly check` lists it.

**The units.** At the end of `store`, after `intermediate.put`:

```java
Dissaly.current().units(read);
```

This is the one that fails silently. `perUnit:` in a scenario is multiplied by
whatever the handler reported, so without this line every map task costs its
`fixed` value whatever the size of its piece, the M comparison shows only the
round count moving, and the flat curve looks like a result.

## 5. Build and check

```bash
./dissaly build
./dissaly check
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main build
> java -cp $cp dissaly.cli.Main check
> ```

`check` must end with **0 edits left**. It also lists two `System.out.print`
calls under "dead rather than wrong", in `JobContext.java` and `RunJob.java`.
Those are supplied code and are expected; they print to the run's own stdout
instead of being attributed to a machine, and nothing else is affected.

`check` greps the source text and **does not skip comments**, so a comment that
mentions a channel builder is enough to produce a finding against the comment
line. The same is true of anything named `Cluster`.

## 6. The first run

```bash
./dissaly simulate simulations/m21-r7.yaml
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml
> ```

The result must report `rows=24`, `total=10000000`, `reduceRounds=1`, and 176
successful calls: 21 map calls, 7 reduce calls, 147 shuffle reads, and the job
call. `mapRounds` counts scheduling passes because the map phase uses a work
queue.

Check that the units edit affects task cost:

```bash
./dissaly simulate simulations/m1-r7.yaml
./dissaly simulate simulations/m140-r7.yaml
```

> **Windows (PowerShell).** We recommend a Codespace or the Dev Container,
> where the block above runs as printed. On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main simulate simulations\m1-r7.yaml
> java -cp $cp dissaly.cli.Main simulate simulations\m140-r7.yaml
> ```

`m1-r7` must fail. `m140-r7` should take about half again as long as `m21-r7`.
If `m1-r7` completes, the handler is not reporting `units(read)` and the
per-unit cost is zero.

The lab is on [port 19843](http://localhost:19843/), and
[`simulations/README.md`](3-distributed/exercise/simulations/README.md) says what
each scenario shows.

---

## Not on this list

**Writing scenarios.** They ship finished. The grid, the overlays and the zone
variant are the evidence Task 5 argues from, and a new one changes what the
argument rests on. M and R are the exception: each scenario carries its own in
the `config:` block of the client's `runs:` entry, and a point off the grid is a
copy of one scenario with those two numbers changed.

**Anything in `src/mapreduce/`.** `Worker.java` and `Heartbeat.java` are the
student's, and so are the holes in `RunJob.java`. When `check` points at
something in there, explain what `Dissaly.current().channelTo`, `alsoHolds` and
`units` are for and let them make the edit.
