# Task 1: Identify characteristics of a distributed system

Each store has key `x`, three replicas in separate data centres, as well as a client
that writes and reads the value. The implementations respond differently when a
replica is unreachable during a write.

Each run includes a **network partition** between two replicas. The replicas
remain alive, i.e. the system recovers from the partition.

Identify each implementation from its run. Do not edit the code.

---

## Procedure

1. Open the console on **port 19841**.
2. Open [QUIZ.md](QUIZ.md) beside it.
3. Press **Run** on `simulations/system-1.yaml`, then on `simulations/system-2.yaml`.
4. Read both runs, tick one box per system in [QUIZ.md](QUIZ.md), and write
   evidence for each.

The results remain under **Runs** for comparison.

---

## Reading runs

The console view gives you the following insights into the systems:

| tab | what it answers |
|---|---|
| **Overview** | Run summary, including accepted and refused writes and rounds with different replica values. |
| **Film** | Animation of the observable sytem. Solid means working; pale means waiting. Drag the playhead to inspect a point in the run. |
| **Usage** | CPU and memory use for each node during the run. |
| **Cost** | Estimated cost for calls, network bytes, and node time. |
| **Runs** | Stored results for comparison. |

A network partition leaves the nodes running. They continue serving reachable
clients. 

### Messages on the film

Every message in a run is a **`Write`** or **`Read`**. This includes replica
messages. The pill on the wire identifies the operation.

| on a message | what it means |
|---|---|
| the two names | source and destination, left to right. The arrowhead points to the destination |
| `→` | request |
| `←` | response |
| `· 28 B` | message size |
| `value 4   version 1,674` | Written value and write version. The version is the clock reading of the replica that accepted the write. A stopped version means that the store stopped accepting writes |
| **red** pill and text | Failed call. Red appears when the call leaves and remains visible during its flight |
| **amber** dashed wire | Message crossing a zone. It adds delay and cost but does not indicate success. |

Colours on successful calls distinguish work for tracing. Their assignment is
arbitrary. 

Pause and **hover a message** to inspect its payload. Hover over a node to see
what data it holds.

### Comparing replica values

Choose **`x`** in the **key picker** above the film to show each replica's
current value. Each display shows a value and version, such as `5  v1763`. A
replica without a write shows a dash.

| key | what each replica puts under it |
|---|---|
| `x` | value returned by a read and its version |
| `last write` | latest write status: committed, refused, or passed on by another replica |

Use `x` to compare current replica values and `last write` to inspect the
latest write in a node panel.

---

## If a run will not start

The console compiles the lab before running it. A red message usually names a
compile error and its file. If the console is absent, choose `Terminal -> Run
Task -> Start Task 1`.

The manual is a separate program on **port 19844**. Use it when the console will
not start.
