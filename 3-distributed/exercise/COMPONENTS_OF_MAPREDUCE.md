# MapReduce and its Components

As you have seen in the lecture, map reduce has several stages (see below). In this file, you can find each components in more detail, combined with questions to consider for those components as well as whether they are already implemented for you.

```
1. Split input  ->  2. Map  ->  3. Shuffle & Sort  ->  4. Reduce  ->  5. Output
```

## Types through the pipeline

Here k1 is the map task index t, v1 is piece t, k2 is a word and v2 is its
count.

| Stage | Input | Output | Done by |
|---|---|---|---|
| 1. Split input | words, seed, M | piece t, for t in 0..M-1 | [`Corpus.java`](src/main/java/infrastructure/Corpus.java), on the Worker |
| 2. Map | (t, piece t) | (word, count), summed per word and split into R groups | `store` in [`Shuffle.java`](src/main/java/infrastructure/Shuffle.java), called from [`Worker.java`](src/main/java/mapreduce/Worker.java) |
| 3. Shuffle & Sort | group p of every map task | every (word, count) of partition p, sorted by word | `Fetch` in [`Shuffle.java`](src/main/java/infrastructure/Shuffle.java); collecting in [`Worker.java`](src/main/java/mapreduce/Worker.java) |
| 4. Reduce | (word, list(count)) | (word, count) | [`Worker.java`](src/main/java/mapreduce/Worker.java) |
| 5. Output | R sorted lists of (word, count) | one sorted list | [`Master.java`](src/main/java/infrastructure/Master.java) |

A Worker runs stages 2 to 4 as two tasks:

```
map task    : (t, piece t)                        -> R groups of (word, count), kept on the Worker
reduce task : (p, group p of map tasks 0..M-1)    -> list(word, count), sorted by word
```

In Task 2, `mapTask` and `reduceTask` in `engine/Pipeline.java` are the same
two tasks on one machine.

---

## Input and splits

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/Corpus.java`](src/main/java/infrastructure/Corpus.java) |

**Questions to consider.**

- Every chunk has to fit on the Worker that gets it. What does too small an M
  cost you when one Worker is slower than the others, or fails? What does too
  large an M cost the Master, which holds state for every task?
- What would change if the input were already sitting on disks spread across the
  cluster, rather than produced wherever it is needed? Which part of
  [Data locality](#data-locality) follows from that difference?

**Additional reading.** Paper §3.1, and §3.4 for data locality. Lecture 2.

## The Master

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/Master.java`](src/main/java/infrastructure/Master.java), [`infrastructure/JobContext.java`](src/main/java/infrastructure/JobContext.java) |

**Questions to consider.**

- Which facts does the Master have to hold for the whole job, and which can it
  forget once a phase ends?
- If the Master dies mid-job, what is lost, and how does that differ from losing
  a Worker?

**Additional reading.** Paper §3.1, §3.2. Lecture 2.

## Task assignment

| Implemented? | relevant files to read/write |
|---|---|
| Yes, apart from what the calls carry | [`mapreduce/RunJob.java`](src/main/java/mapreduce/RunJob.java) |


**Questions to consider.**

- A round is only as fast as its slowest member. How can we mitigate this?
- A Master that refilled each Worker the moment it went idle would not have that
  problem. What would it cost, and what would it need to know that?

**Additional reading.** Paper §3.1, §3.2. Lecture 2.

## The map worker

| Implemented? | relevant files to read/write |
|---|---|
| Partly | [`infrastructure/Shuffle.java`](src/main/java/infrastructure/Shuffle.java) is given; [`worker.proto`](src/main/proto/worker.proto) and [`mapreduce/Worker.java`](src/main/java/mapreduce/Worker.java) are yours |


**Questions to consider.**

- What does a Worker need to be told so that it can run one map task, when the
  input for that task can be produced where it is rather than shipped to it?
- What does the Master need back when a map task finishes?
- The rule for what counts as a word lives in every machine's code rather than
  travelling in a message. What breaks if two machines disagree about it, and how
  would you notice?

**Additional reading.** Paper §3.1. Lecture 2.

## The partitioning function

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/Shuffle.java`](src/main/java/infrastructure/Shuffle.java) |

**Questions to consider.**

- Any Worker may have counted `the`. Which machine guarantees that every partial
  count for that key reaches one reduce task?
- Partitioning on the consuming machine instead would mean sending everything to
  everybody and discarding what is not yours. What does that cost?

**Additional reading.** Paper §4.1, and §4.3 for the combiner. Lecture 2.

## The combiner

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/Shuffle.java`](src/main/java/infrastructure/Shuffle.java) |


**Questions to consider.**

- A combiner runs the reduce function over one machine's own output before that
  output is sent anywhere. Which property must a reduce function have for that to
  be safe, and do both of the jobs you wrote in Task 2 have it?
- What a combiner saves depends on how many distinct keys one map task produces.
  When does it collapse a map task's output to almost nothing, and when does it
  save nothing at all?

**Additional reading.** Paper §4.3. Lecture 2.

## Shuffle & Sort

| Implemented? | relevant files to read/write |
|---|---|
| Partly | [`infrastructure/Shuffle.java`](src/main/java/infrastructure/Shuffle.java) and the `Fetch` rpc are given; collecting is yours, in [`mapreduce/Worker.java`](src/main/java/mapreduce/Worker.java) |

**Questions to consider.**

- Where should the intermediate pairs be put in key order, and what does choosing
  that place save the Master?
- If a reduce worker pulls its pairs, nothing crosses the network until the
  Reduce phase begins. What would pushing each group to its reduce task as soon
  as it was produced change, and what would it cost?
- What holds the intermediate pairs between the two phases, and what is lost with
  the machine holding them?

**Additional reading.** Paper §3.1. Lecture 2, where the reduce worker fetches
its own pairs from every map worker.

## The reduce worker

| Implemented? | relevant files to read/write |
|---|---|
| Partly | [`worker.proto`](src/main/proto/worker.proto) and [`mapreduce/Worker.java`](src/main/java/mapreduce/Worker.java) |

**Questions to consider.**

- What does a Worker need to be told so that it can run reduce task *p*, when the
  input for it is spread over every machine that ran a map task?
- How large can one reduce task's result get, and what would you do with a job
  whose result is larger than a single reply can carry?
- The Master merges whatever a reduce task gives it. If a reduce task could only
  read part of its input, how does its answer say so, rather than being merged as
  though it were complete?
- A reduce task can fail because a machine it needs is gone, or because a machine
  answered and did not have the pairs it wanted. Why does the Master have to tell
  those two apart, and what does confusing them cost?

**Additional reading.** Paper §3.1, §4.2. Lecture 2.

## The output

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/Master.java`](src/main/java/infrastructure/Master.java), [`infrastructure/GrpcUserProgram.java`](src/main/java/infrastructure/GrpcUserProgram.java) |

**Questions to consider.**

- The final merge sums whatever it is given. Which partitioning mistakes would it
  hide, and how would you catch them?
- What would you change if the output did not fit in one reply, or on one
  machine?

**Additional reading.** Paper §3.1. Lecture 2.

## Heartbeat

| Implemented? | relevant files to read/write |
|---|---|
| Partly | the call is in [`infrastructure/Master.java`](src/main/java/infrastructure/Master.java); the answer is yours, in [`mapreduce/Heartbeat.java`](src/main/java/mapreduce/Heartbeat.java) |


**Questions to consider.**

- What does a returned ping guarantee, and what is the reply allowed to touch?
  "Still alive" and "still working" are different claims.
- What does answering cost on a machine that is already overloaded, which is
  exactly when the answer matters most?
- A false failure re-runs completed map tasks. An undetected one blocks every
  machine waiting on that machine's pairs. Which does your design make more
  likely?

**Additional reading.** Paper §3.3. Lecture 2.

## Map worker failure and reduce worker failure

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`mapreduce/RunJob.java`](src/main/java/mapreduce/RunJob.java), [`infrastructure/JobContext.java`](src/main/java/infrastructure/JobContext.java) |

**Questions to consider.**

- Why is a completed map task re-executed and a completed reduce task not?
- A reduce task has to be told where to find its input. Should that name every
  machine the job started with, or only the ones still answering? What goes wrong
  either way?
- When a machine is written off, what has to happen before the Reduce phase can
  run, and why can a reduce task not simply skip the pairs that went with it?

**Additional reading.** Paper §3.3. Lecture 2. In a `.proto` file the same
property appears as `idempotency_level`, which is what allows a call to be
retried at all; the tutorial meets it directly.

## Backup tasks

| Implemented? | relevant files to read/write |
|---|---|
| No | |

Nothing here runs a task twice. `RunJob.writeOff` is where it would go: its
signature can already say "this machine is alive, leave it live, and hand its
task to somebody else as well", and it never does.

**Questions to consider.**

- A machine that is slow but still answering is never declared dead. What does
  one such machine do to every round of work it appears in, and what would
  running its task on a second machine as well have saved?
- When is a task late enough to be worth running twice, and what does the second
  copy cost in machines and in messages?
- Which result does the Master keep, and what happens to the other one when it
  arrives?

**Additional reading.** Paper §3.6. Lecture 2.

## Data locality

| Implemented? | relevant files to read/write |
|---|---|
| No | |

**Questions to consider.**

- Which traffic here crosses the network no matter where the machines sit, and
  which would disappear if two of them shared one?
- If half the machines sat in a distant data centre, which traffic would get
  slower? What keeps that cheap for a word count, and what kind of job would make
  it expensive?

**Additional reading.** Paper §3.4. Lecture 2.

## Task granularity

| Implemented? | relevant files to read/write |
|---|---|
| Yes | [`infrastructure/GrpcUserProgram.java`](src/main/java/infrastructure/GrpcUserProgram.java) |


**Questions to consider.**

- What does raising M do to the round count, to the Master's per-task
  bookkeeping, and to the cost of losing one Worker?
- What does raising R do to the number of Shuffle & Sort transfers, and to the
  size of each one?
- Lecture 2 wants M much larger than the Worker count and R a small multiple of
  it. Where does each half of that rule come from, and when would you break it?

**Additional reading.** Paper §3.5. Lecture 2.
