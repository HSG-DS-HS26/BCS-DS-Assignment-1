package mapreduce;

import com.google.common.util.concurrent.ListenableFuture;
import infrastructure.JobContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mapreduce.pb.MapDone;
import mapreduce.pb.MapTask;
import mapreduce.pb.ReduceDone;
import mapreduce.pb.ReduceTask;
import mapreduce.pb.WorkerGrpc;

/**
 * =============================================================== provided
 *
 * The Master calls this class once for each phase and supplies a
 * {@link JobContext}. The supplied code keeps one task in flight per live
 * Worker, collects future replies, retries failed tasks, and removes
 * unreachable Workers.
 *
 * <p>{@code newFutureStub} starts an RPC without waiting for it. The simulator
 * rejects application-created threads, so this is the concurrency mechanism
 * used here.
 *
 * <p>The map phase refills a Worker's slot when it completes a task. The reduce
 * phase assigns work in rounds so {@link JobContext#remap} runs before each
 * round. {@code countMapRound} therefore counts scheduling passes.
 *
 * <p>=========================================================== student work
 *
 * <p>TODO(student): Complete {@link #mapRequest}, {@link #reduceRequest}, and
 * the marked merge of a reduce reply. The supplied scheduling and failure
 * handling code must remain unchanged.
 */
public final class RunJob {

    // =============================================================== provided ==

    /** How long a wait on one outstanding call blocks before looking again. */
    private static final long POLL_MS = 1;

    public void assignMapTasks(Deque<Integer> pending, JobContext job, boolean firstRun) {
        var attempts = new HashMap<Integer, Integer>();

        // Each Worker has one task in flight. Completing a task frees its slot.
        var inflight = new LinkedHashMap<String, Integer>();
        var flight = new LinkedHashMap<Integer, ListenableFuture<MapDone>>();

        while (!pending.isEmpty() || !inflight.isEmpty()) {
            // Fill every idle Worker before waiting on any of them.
            boolean issued = false;
            for (String worker : job.live()) {
                if (pending.isEmpty()) break;
                if (inflight.containsKey(worker)) continue;
                int task = pending.poll();
                inflight.put(worker, task);
                flight.put(task, WorkerGrpc.newFutureStub(job.channel(worker))
                        .withDeadlineAfter(job.taskDeadlineMs(), TimeUnit.MILLISECONDS)
                        .runMapTask(mapRequest(task, job)));
                issued = true;
            }
            if (inflight.isEmpty()) {
                throw new IllegalStateException("every Worker has failed: " + job.failed());
            }
            if (issued) {
                job.countMapRound();
            }

            // Scan completed calls, then wait briefly on one outstanding call.
            String worker = null;
            int task = -1;
            while (worker == null) {
                for (var slot : inflight.entrySet()) {
                    if (flight.get(slot.getValue()).isDone()) {
                        worker = slot.getKey();
                        task = slot.getValue();
                        break;
                    }
                }
                if (worker == null) {
                    try {
                        flight.get(inflight.values().iterator().next())
                                .get(POLL_MS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException waitingOnAnother) {
                        // Scan all calls again.
                    } catch (InterruptedException | ExecutionException failed) {
                        // The next scan observes the completed failure.
                    }
                }
            }

            inflight.remove(worker);
            ListenableFuture<MapDone> answered = flight.remove(task);
            try {
                // The reply is the acknowledgement. The Master already knows
                // which machine it called, so whatever else MapDone carries
                // is a decision you made and can defend.
                answered.get();
                job.mapTaskFinished(task, worker, firstRun);
                System.out.println("map task " + task + " on " + worker + " done");
            } catch (InterruptedException | ExecutionException e) {
                Status.Code code = codeOf(e);
                System.out.println("map task " + task + " on " + worker + " failed: " + code);
                if (writeOff(job, worker, code)) job.strikeOff(worker);
                job.mapTaskFailed(worker, firstRun);
                job.giveUp(attempts, task, "map task " + task);
                pending.add(task);
            }
        }
    }

    public void assignReduceTasks(TreeMap<String, Long> answer, JobContext job) {
        var pending = new ArrayDeque<Integer>();
        for (int partition = 0; partition < job.reduceTasks(); partition++) pending.add(partition);
        var attempts = new HashMap<Integer, Integer>();
        int turn = 0;

        while (!pending.isEmpty()) {
            // Before every round, not just the first: a Worker can be lost during
            // the Reduce phase, and a reduce task sent looking for its pairs on a
            // machine that is gone fails for a reason retrying cannot fix.
            job.remap();

            var round = new LinkedHashMap<String, Integer>();
            var flight = new LinkedHashMap<Integer, ListenableFuture<ReduceDone>>();
            var all = job.live();
            for (int i = 0; i < all.size() && !pending.isEmpty(); i++) {
                String worker = all.get(Math.floorMod(turn + i, all.size()));
                int partition = pending.poll();
                round.put(worker, partition);
                flight.put(partition, WorkerGrpc.newFutureStub(job.channel(worker))
                        .withDeadlineAfter(job.taskDeadlineMs(), TimeUnit.MILLISECONDS)
                        .runReduceTask(reduceRequest(partition, job)));
            }
            if (round.isEmpty()) {
                throw new IllegalStateException("every Worker has failed: " + job.failed());
            }
            turn++;
            job.countReduceRound();

            for (var assigned : round.entrySet()) {
                String worker = assigned.getKey();
                int partition = assigned.getValue();
                try {
                    ReduceDone done = flight.get(partition).get();

                    // TODO(student): Merge this reply into `answer`, one summed entry per
                    // key. Summed, because a key belongs to exactly one reduce
                    // task and so appears in exactly one of these replies; if
                    // that is ever untrue, this merge hides it, which is what
                    // `./gradlew partitionCheck` exists to catch.
                    //
                    //     answer.merge(key, value, Long::sum);

                    job.reduceTaskFinished();
                    System.out.println("reduce task " + partition + " on " + worker + " done");
                } catch (InterruptedException | ExecutionException e) {
                    Status.Code code = codeOf(e);
                    System.out.println("reduce task " + partition + " on " + worker
                            + " failed: " + code);
                    if (writeOff(job, worker, code)) job.strikeOff(worker);

                    // The machine that failed this call is usually not the machine
                    // that caused it. A reduce task dies because a *holder* would
                    // not answer, and the holder's name never reaches here: it is
                    // prose inside a status description. So ask the holders
                    // directly, or the dead one keeps its place in holders(),
                    // remap() finds nothing to do, and this task retries into the
                    // same wall until giveUp stops it.
                    writeOffSilentHolders(job);

                    job.reduceTaskFailed(worker);
                    job.giveUp(attempts, partition, "reduce task " + partition);
                    pending.add(partition);
                }
            }
        }
    }

    // =============================================================== provided ==

    /**
     * Writes off any machine still believed to hold map output that has stopped
     * answering. Called after a reduce task fails, which is the moment the job
     * first touches a holder it may not have spoken to since the Map phase.
     *
     * <p>Without this, a machine that finishes its map tasks and then dies is
     * found only when the round rotation happens to hand it a reduce task of its
     * own, which is recovery by luck rather than by design, and the luck runs out
     * when R is smaller than the number of Workers. With it, the next
     * {@link JobContext#remap} re-runs exactly what that machine was holding.
     *
     * <p>Bounded: each distinct live holder is asked once, so at most one ping per
     * machine, only on a failure, and never for a machine already written off.
     */
    private void writeOffSilentHolders(JobContext job) {
        var live = new LinkedHashSet<>(job.live());
        var asked = new LinkedHashSet<String>();
        for (String holder : job.holders()) {
            if (holder == null || !live.contains(holder) || !asked.add(holder)) continue;
            if (!job.probe(holder) && !job.probe(holder)) job.strikeOff(holder);
        }
    }

    /**
     * Is this machine gone, or is it just slow?
     *
     * <p>The status code is evidence. A reply the Worker composed itself proves
     * the machine is alive and answering, whatever it says. A reduce task that
     * reports a missing holder with {@code DATA_LOSS}, or a call refused with
     * {@code UNIMPLEMENTED}, is a machine to keep: writing it off there would
     * throw away every map task it holds over somebody else's failure.
     *
     * <p>Silence is asked about twice. A single dropped ping would be enough to
     * lose a machine, and losing a live machine costs every map task it was
     * holding; a second question costs one more ping and one more
     * {@code Master.PING_MS}. Under a straggler that is the difference between a
     * slow machine kept and a slow machine written off, which is why the
     * heartbeat handler has to be as cheap as it is.
     */
    private boolean writeOff(JobContext job, String worker, Status.Code code) {
        // The machine answered; something else went wrong.
        if (code == Status.Code.UNIMPLEMENTED
                || code == Status.Code.INVALID_ARGUMENT
                || code == Status.Code.FAILED_PRECONDITION
                || code == Status.Code.DATA_LOSS
                || code == Status.Code.INTERNAL) {
            return false;
        }
        // Silence. Ask twice before believing it.
        return !job.probe(worker) && !job.probe(worker);
    }

    /** The status code inside whatever a future threw. */
    private static Status.Code codeOf(Throwable thrown) {
        Throwable cause = thrown instanceof ExecutionException ? thrown.getCause() : thrown;
        return cause instanceof StatusRuntimeException status
                ? status.getStatus().getCode()
                : Status.Code.UNKNOWN;
    }

    // =========================================================== student work ==

    /**
     * Builds the request for map task {@code task}.
     *
     * <p>The Worker generates its input from its local
     * {@code infrastructure.Corpus}. {@link JobContext} lists the facts the
     * Master can send. Include only facts the Worker needs to run the task.
     */
    private MapTask mapRequest(int task, JobContext job) {
        // TODO(student): Add the fields the Worker needs. With only the task
        // number, it generates an empty piece and the job counts nothing.
        return MapTask.newBuilder()
                .setTask(task)
                .build();
    }

    /**
     * Builds the request for reduce task {@code partition}.
     *
     * <p>The request must reach the output for every map task. Build it when the
     * task is assigned so it reflects the current {@link JobContext#holders},
     * including work rerun by {@link JobContext#remap}.
     */
    private ReduceTask reduceRequest(int partition, JobContext job) {
        // TODO(student): Add the fields the Worker needs to collect its input.
        return ReduceTask.newBuilder()
                .setPartition(partition)
                .build();
    }
}
