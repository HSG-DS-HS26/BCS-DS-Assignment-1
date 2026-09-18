# Task 5: pitch your solution (2 pt)


## Background

Tundrell Group needs word counts for ten million words of product descriptions
and supplier correspondence. In task 3 you built the cluster. In this task you investigate the
configuration Tundrell should use.

The Task 3 plain gRPC check counts 20,000 words. Task 5 deliberately uses
10,000,000 words in its simulations so workload configuration affects duration
and failure behaviour.

Adopt the gRPC system with [`TRANSFORMATION.md`](../TRANSFORMATION.md), then run
the scenarios described in the adjacent [`README.md`](README.md). Present a
five-minute pitch on cost, performance, failed Workers, and slow Workers for an
audience unfamiliar with the assignment or source code.

Every visual and numeric claim about system behaviour must be output from a
DISSALy run that you performed: console screenshots, film, or values shown by
the console. Hand-drawn diagrams, invented timings, and self-made charts are not
evidence.

## Scenarios

[`3-distributed/exercise/simulations/`](../3-distributed/exercise/simulations/)
provides the scenarios. Analyze the adopted system through these runs.

| | values | fix |
|---|---|---|
| M | 1, 7, 21, 70, 140 | R = 7 |
| R | 1, 3, 7, 14, 21 | M = 21 |
| a failed Worker | `overlay-kill.yaml` | applies to any of the above |
| a slow Worker | `overlay-straggler.yaml` | applies to any of the above |
| three slow Workers | `overlay-stragglers.yaml` | 2x, 4x and 8x; applies to any of the above |
| two zones | `m21-r7-split-zone.yaml` | M = 21, R = 7 |

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

Use the Task 1 console techniques to analyze each configuration.

## Preparation

Choose three configurations and present DISSALy evidence for each:

- M and R, and why those values for seven Workers;
- cost and duration;
- the effect and cost of one failed Worker;
- the effect and cost of one slow Worker;
- one advantage and one disadvantage.

Recommend one. State which alternative you reject, what it loses, and a
condition that would change your decision. Include one result that differed from
your prediction.

## Presentation

Use one or two slides for each scenario. Explain the decision and its cost;
avoid a source-code walkthrough.

Run simulations at [localhost:19843](http://localhost:19843/). Capture moments
with **png** or **svg**, download the film, and use **Overview**, **Usage**, and
**Cost** for the values you report. Check the result line: `dissaly simulate`
ignores unknown flags and still exits 0.

Choose one submission option for both presentation files:

- Commit and push both files in the repository:

  ```
  5-presentation/
    slides.pdf
    presentation.mp4
  ```

- Upload both files to Canvas with the generated submission PDF. Keep the
  Canvas-only copies outside the repository before running **Hand in**.

Record screen and voice for five minutes or less. Keep the recording below
100 MB. 1280x720, 30 fps, and H.264 mp4 usually fit; `.mov` and `.webm` are
also accepted.

## Grading

Grading covers the presentation and its alignment with the source code. The
configurations must differ in M and R. Every behaviour claim must come from a
run you performed. End with a recommendation for technical management.
