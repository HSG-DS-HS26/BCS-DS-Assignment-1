# Simulations

These supplied scenarios provide the comparisons for Task 5. Adopt the project
before running them: each scenario uses post-adoption paths. See
[`../../../TRANSFORMATION.md`](../../../TRANSFORMATION.md).

```bash
./dissaly simulate simulations/m21-r7.yaml
```

> **Windows (PowerShell).** On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml
> ```
>
> `./dissaly` is a `sh` script and adoption writes no `.bat` beside it, which is
> why this box has three lines. [Running the commands on
> Windows](../../../README.md#running-the-commands-on-windows) explains them.

## Scenarios

Every scenario counts the same ten million words on seven Workers. The M and R
values change the job's granularity and resource use.

The grid scenarios use the same Workers. Their M and R values appear in the
`config:` block of the client's `runs:` entry beside the program that reads them.

| | M | R | what it shows |
|---|---|---|---|
| `m21-r7.yaml` | 21 | 7 | baseline for the other scenarios |
| `m1-r7.yaml` | 1 | 7 | **fails**: one task cannot finish inside its deadline |
| `m7-r7.yaml` | 7 | 7 | **fails**: one task per Worker is still too much input |
| `m70-r7.yaml` | 70 | 7 | smaller map pieces; best result under a straggler |
| `m140-r7.yaml` | 140 | 7 | per-task overhead dominates the map phase |
| `m21-r1.yaml` | 21 | 1 | a single reducer merges all output |
| `m21-r3.yaml` | 21 | 3 | fewer reduce tasks than Workers |
| `m21-r14.yaml` | 21 | 14 | an additional reduce round and more shuffle reads |
| `m21-r21.yaml` | 21 | 21 | further reduce rounds and shuffle reads |

The failed scenarios identify the lower bound for M. Each map task processes
`words / M`; a task with too much input exceeds `Master.TASK_MS` even on a
healthy Worker. With seven Workers, the bound is M = 9.

Run each remaining configuration without failures and with each overlay. Use
the measurements to choose Tundrell's configuration.

## Failure overlays

An overlay adds a failure to a base scenario. It allows the same failure to be
compared across different M and R values.

```bash
./dissaly simulate simulations/m21-r7.yaml --overlay simulations/overlay-kill.yaml
./dissaly simulate simulations/m21-r7.yaml --overlay simulations/overlay-straggler.yaml
./dissaly simulate simulations/m21-r7.yaml --overlay simulations/overlay-stragglers.yaml
```

> **Windows (PowerShell).** On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml --overlay simulations\overlay-kill.yaml
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml --overlay simulations\overlay-straggler.yaml
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml --overlay simulations\overlay-stragglers.yaml
> ```

`overlay-kill.yaml` kills `w3` while it holds map output and exercises the
recovery path. `overlay-straggler.yaml` slows `w5` while it continues to answer,
exercising the write-off rule.

`overlay-stragglers.yaml` distributes slowdowns across several Workers. Compare
it with `overlay-straggler.yaml`. Each round waits for its slowest Worker.

An overlay may set `seed`, `network`, `retries`, and a node's `failures:`. It
cannot define nodes or run alone. `simulate` then reports *"'instance' is
required here"*.

Task 5 also permits changes to those settings when marking a measurement point.

## Optional zone scenario

`m21-r7-split-zone.yaml` puts the Workers in two zones. A cross-zone shuffle read
costs six times as much as a local read. This must be a scenario because `zone`
is a node key that an overlay cannot contain.

It runs about seven per cent slower than the baseline. The combiner reduces the
data transferred between zones, so pricing alone predicts a larger difference.

## Allowed changes

Copy a scenario to measure a point outside the grid, then change its M and R
values in the `config:` block. Task 5 asks for no other scenario authoring.

Keep the node count, instance types, network, prices, and failures unchanged.
Compared runs should differ by one variable.
