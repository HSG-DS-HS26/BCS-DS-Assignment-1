# Task 5: simulation runs

Adopt [`3-distributed/exercise`](../3-distributed/exercise/) after the plain
gRPC run prints 24 rows and a total of 20 000. Task 5 simulations use
10 000 000 words.

> **Windows.** The commands use a POSIX shell. Codespaces and the Dev Container
> provide one. PowerShell alternatives appear below each command block; see
> [Running the commands on Windows](../README.md#running-the-commands-on-windows)
> for the command translations.

## Adopt

Ask your AI agent to do the adoption
[`TRANSFORMATION.md`](../TRANSFORMATION.md) guides the agent to implement it correclty for you
once you have your gRPC system running. The lab runs on [port 19843](http://localhost:19843/).

## Run scenarios

[`simulations/`](../3-distributed/exercise/simulations/) contains the M/R grid,
failure overlays, and a (optional) zone scenario. Run them from
`3-distributed/exercise/`:

```bash
./dissaly simulate simulations/m21-r7.yaml
./dissaly simulate simulations/m21-r7.yaml --overlay simulations/overlay-kill.yaml
```

> **Windows (PowerShell).** On a Windows host:
>
> ```powershell
> .\gradlew.bat -q dissalyToolchain
> $cp = (Get-Content build\dissaly\classpath -Raw).Trim()
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml
> java -cp $cp dissaly.cli.Main simulate simulations\m21-r7.yaml --overlay simulations\overlay-kill.yaml
> ```

An overlay adds a straggler or partial failure to a base scenario. It may set `seed`, `network`,
`retries`, and a node's `failures:`. An overlay has no nodes, so run it with a base
scenario. Running an overlay alone reports *"'instance' is required here"*.

[`simulations/README.md`](../3-distributed/exercise/simulations/README.md)
describes the scenarios and the permitted M/R changes.

## Reference

The [simulator manual](http://localhost:19844/) matches the pinned version.

| page | topic |
|---|---|
| [adopting a gRPC system](http://localhost:19844/start/adopt) | migration |
| [simulation file](http://localhost:19844/ref/simulation) | top-level keys |
| [`simulatedDuration`](http://localhost:19844/ref/simulated-duration) | call cost |
| [concurrency](http://localhost:19844/write/concurrency) | node thread pools and queueing |
| [`dissaly simulate`](http://localhost:19844/ref/cli-simulate) | `--overlay`, `--seed` |
| [failures](http://localhost:19844/ref/failures), [retries](http://localhost:19844/ref/retries) | kills, slowdowns, and what is retried |
| [nodes](http://localhost:19844/ref/nodes), [network](http://localhost:19844/ref/network), [prices](http://localhost:19844/ref/prices) | resources and cost |
| [`dissaly.Job`](http://localhost:19844/ref/dissaly-proto), [Dissaly](http://localhost:19844/ref/api-dissaly) | schema and API |
| [input](http://localhost:19844/ref/input) | workload input |
| [refusals](http://localhost:19844/simulate/refusals) | rejected simulations |
