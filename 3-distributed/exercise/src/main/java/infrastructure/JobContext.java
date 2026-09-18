package infrastructure;

import io.grpc.Channel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import mapreduce.RunJob;
import mapreduce.pb.JobRequest;

/**
 * Supplied state and operations for one job.
 *
 * <p>{@link Master} creates one context per job and passes it to {@code RunJob}.
 * This class handles re-execution and bookkeeping. Your code controls assignment
 * and the policy for failed Workers.
 *
 * <p>The API exposes job facts, Worker communication, bookkeeping and state
 * changes. It does not choose assignment order, concurrency or failed-call policy.
 *
 * <p>Mutating methods are synchronised. Sequences of calls remain non-atomic:
 * a Worker may be struck off after {@link #live} returns. {@link #live} and
 * {@link #failed} return snapshots.
 */
public final class JobContext {

    private final Master master;
    private final RunJob runJob;

    private final JobRequest request;
    private final int mapTasks;
    private final int reduceTasks;
    private final List<String> allWorkers;

    private final Set<String> live;
    private final Set<String> failed = new LinkedHashSet<>();

    /**
     * Which Worker holds the intermediate pairs for each map task. Null until
     * that task has run somewhere, and null again once its Worker is lost.
     */
    private final String[] holder;

    private final AtomicInteger reexecuted = new AtomicInteger();

    JobContext(Master master, RunJob runJob, JobRequest request) {
        this.master = master;
        this.runJob = runJob;
        this.request = request;
        this.allWorkers = List.copyOf(request.getWorkersList());
        if (allWorkers.isEmpty()) {
            throw new IllegalArgumentException("a job needs at least one Worker");
        }
        this.mapTasks = Math.max(1, request.getMapTasks());
        this.reduceTasks = Math.max(1, request.getReduceTasks());
        this.live = new LinkedHashSet<>(allWorkers);
        this.holder = new String[this.mapTasks];
    }

    // ------------------------------------------------------- what the job is

    /** The request the user program submitted. Its words and seed feed a MapTask. */
    public JobRequest request() { return request; }

    /** M, the number of map tasks. At least one. */
    public int mapTasks() { return mapTasks; }

    /** R, the number of reduce tasks, and so the number of groups a map task
     *  splits its pairs into. At least one. */
    public int reduceTasks() { return reduceTasks; }

    /**
     * Every Worker address present at job start, including failed Workers.
     * Workers use this stable list to agree on map-task pair holders.
     */
    public List<String> allWorkers() { return allWorkers; }

    /** The Workers still answering, in the order the job started them. A snapshot. */
    public synchronized List<String> live() { return new ArrayList<>(live); }

    /** The Workers written off as failed. A snapshot. */
    public synchronized List<String> failed() { return new ArrayList<>(failed); }

    /**
     * Who holds the intermediate pairs for each map task, indexed by task. A
     * snapshot, and the list a ReduceTask carries so those pairs can be found.
     */
    public synchronized List<String> holders() { return new ArrayList<>(Arrays.asList(holder)); }

    // -------------------------------------------------- reaching the machines

    /**
     * Reusable channel to one Worker. Do not create one per call.
     */
    public Channel channel(String worker) { return master.channel(worker); }

    /**
     * Deadline for map and reduce task calls.
     */
    public int taskDeadlineMs() { return Master.TASK_MS; }

    /**
     * Sends the liveness RPC and reports whether the Worker replied.
     *
     * <p>{@link Master} defines this call and its shorter deadline. A reply shows
     * that the Worker is reachable. This method does not change job state; your
     * design determines what follows a missing reply.
     */
    public boolean probe(String worker) { return master.alive(worker); }

    /**
     * Marks a Worker as failed. It leaves {@link #live}, enters {@link #failed},
     * appears in the job result, and receives no further assignments.
     *
     * <p>Only this method writes off a Worker. {@link #remap} re-runs map tasks
     * held by a Worker that has been struck off. Repeated calls are safe.
     */
    public synchronized void strikeOff(String worker) {
        if (live.remove(worker)) {
            failed.add(worker);
            System.out.println(worker + " written off as failed");
        }
    }

    /** Next live Worker in round-robin order. */
    public synchronized String next(int turn) {
        if (live.isEmpty()) throw new IllegalStateException("every Worker has failed: " + failed);
        var all = new ArrayList<>(live);
        return all.get(Math.floorMod(turn, all.size()));
    }

    // ----------------------------------------------------------- task bookkeeping

    /**
     * Records that map task {@code task} ran on {@code worker} and its pairs are
     * now sitting there.
     *
     * @param firstRun counters only: a completed task counts towards progress the
     *                 first time and towards {@code tasks_reexecuted} afterwards.
     */
    public synchronized void mapTaskFinished(int task, String worker, boolean firstRun) {
        holder[task] = worker;
        if (firstRun) master.mapTasksDone.incrementAndGet();
        else reexecuted.incrementAndGet();
    }

    /**
     * Records a failed map task. Updates counters only; it does not write off the
     * Worker or re-queue the task.
     */
    public synchronized void mapTaskFailed(String worker, boolean firstRun) {
        if (firstRun) reexecuted.incrementAndGet();
    }

    /** Records that a reduce task came back. Merging its pairs is yours. */
    public synchronized void reduceTaskFinished() {
        master.reduceTasksDone.incrementAndGet();
    }

    /** As {@link #mapTaskFailed}, for a reduce task. Counters only. */
    public synchronized void reduceTaskFailed(String worker) {
        reexecuted.incrementAndGet();
    }

    /**
     * Records one batch of work assigned and awaited. Call once per batch, not
     * per task. The job result exposes this count as {@code map_rounds}.
     */
    public void countMapRound() { master.mapRounds.incrementAndGet(); }

    /** As {@link #countMapRound}, for the Reduce phase. */
    public void countReduceRound() { master.reduceRounds.incrementAndGet(); }

    // ---------------------------------------------------------- supplied rules

    /**
     * Re-runs map tasks whose intermediate pairs were lost with a struck-off
     * Worker, by calling {@link RunJob#assignMapTasks}.
     *
     * <p>Call before assigning a reduce task. Completed reduce tasks are not
     * re-run because their output has returned to the Master.
     */
    public void remap() {
        var lost = new ArrayDeque<Integer>();
        synchronized (this) {
            for (int task = 0; task < mapTasks; task++) {
                if (holder[task] != null && live.contains(holder[task])) continue;
                System.out.println("map task " + task + " must run again: "
                        + holder[task] + " has failed");
                holder[task] = null;
                lost.add(task);
            }
        }
        if (lost.isEmpty()) return;
        runJob.assignMapTasks(lost, this, false);
    }

    /**
     * Stops retrying a task no Worker accepts. Call each time the task returns to
     * the queue.
     *
     * @param attempts your own map, one per assignment loop, counting how often
     *                 each task has come back failed
     */
    public synchronized void giveUp(Map<Integer, Integer> attempts, int task, String what) {
        int made = attempts.merge(task, 1, Integer::sum);
        int allowed = Math.max(3, live.size() * 2);
        if (made >= allowed) {
            throw new IllegalStateException(what + " failed " + allowed + " times."
                    + " The Worker may refuse the task, or the failure policy may not have"
                    + " identified a failed machine. Live: " + live + ", failed: " + failed);
        }
    }

    // --------------------------------------------------- for the Master alone

    int reexecuted() { return reexecuted.get(); }
}
