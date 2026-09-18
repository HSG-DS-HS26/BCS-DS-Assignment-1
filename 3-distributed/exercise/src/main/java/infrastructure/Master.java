package infrastructure;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mapreduce.RunJob;
import mapreduce.pb.Alive;
import mapreduce.pb.Beat;
import mapreduce.pb.Empty;
import mapreduce.pb.HeartbeatGrpc;
import mapreduce.pb.JobRequest;
import mapreduce.pb.JobResult;
import mapreduce.pb.MasterGrpc;
import mapreduce.pb.Pair;
import mapreduce.pb.Phase;
import mapreduce.pb.ProgressReply;

/**
 * Supplied Master service. It receives a job and returns its result.
 *
 * <p>This class initialises a job, advances it through Map and Reduce, and calls
 * your {@link mapreduce.RunJob} for task assignment. It passes a
 * {@link JobContext} containing job state and supplied operations.
 *
 * <h4>Supplied behaviour</h4>
 *
 * <ul>
 *   <li>{@link JobContext#probe} performs a short liveness RPC. Your
 *       {@code RunJob} determines the policy for missing replies and calls
 *       {@link JobContext#strikeOff} when appropriate.
 *   <li>{@link JobContext#remap} re-executes completed map tasks after their
 *       Worker is lost. Completed reduce tasks are already at the Master.
 *   <li>A Worker written off may later return a reply, which the Master drops.
 *   <li>The Master is a single point of failure; rerunning the job is the
 *       supplied recovery path.
 * </ul>
 */
public abstract class Master extends MasterGrpc.MasterImplBase {

    /**
     * How long a map or reduce task may take before the Master asks after it.
     *
     * <p>Each map task processes {@code words / M} words. The simulation charges
     * the declared per-unit cost for that input. Small M values can therefore
     * exceed this deadline on a healthy Worker. Task 5 scenarios test values on
     * both sides of that threshold.
     */
    static final int TASK_MS = 70_000;

    /** Maximum heartbeat duration. */
    static final int PING_MS = 1_000;

    /** Assignment strategy, reused for every job. */
    private final RunJob runJob = new RunJob();


    // Progress state for the active job.
    private volatile Phase phase = Phase.IDLE;
    final AtomicInteger mapTasksDone = new AtomicInteger();
    final AtomicInteger mapTasksTotal = new AtomicInteger();
    final AtomicInteger reduceTasksDone = new AtomicInteger();
    final AtomicInteger reduceTasksTotal = new AtomicInteger();

    /** Number of assigned-and-awaited batches. */
    final AtomicInteger mapRounds = new AtomicInteger();
    final AtomicInteger reduceRounds = new AtomicInteger();

    // ------------------------------------------------------------------ rpcs

    @Override public void runJob(JobRequest req, StreamObserver<JobResult> out) {
        try {
            out.onNext(run(req));
            out.onCompleted();
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription(e.toString()).asRuntimeException());
        }
    }

    @Override public void progress(Empty req, StreamObserver<ProgressReply> out) {
        out.onNext(ProgressReply.newBuilder()
                .setPhase(phase)
                .setMapTasksDone(mapTasksDone.get()).setMapTasksTotal(mapTasksTotal.get())
                .setReduceTasksDone(reduceTasksDone.get()).setReduceTasksTotal(reduceTasksTotal.get())
                .build());
        out.onCompleted();
    }

    // --------------------------------------------------------- job execution

    /**
     * Runs one job. Calls to {@link RunJob} assign map and reduce work.
     */
    private JobResult run(JobRequest req) {
        var job = new JobContext(this, runJob, req);

        mapRounds.set(0);
        reduceRounds.set(0);
        mapTasksTotal.set(job.mapTasks());
        mapTasksDone.set(0);
        reduceTasksTotal.set(job.reduceTasks());
        reduceTasksDone.set(0);

        phase = Phase.MAP;
        var pending = new ArrayDeque<Integer>();
        for (int task = 0; task < job.mapTasks(); task++) pending.add(task);
        runJob.assignMapTasks(pending, job, true);

        phase = Phase.REDUCE;
        var answer = new TreeMap<String, Long>();
        runJob.assignReduceTasks(answer, job);

        phase = Phase.DONE;

        var reply = JobResult.newBuilder()
                .setMapTasksRun(job.mapTasks())
                .setReduceTasksRun(job.reduceTasks())
                .setTasksReexecuted(job.reexecuted())
                .setMapRounds(mapRounds.get())
                .setReduceRounds(reduceRounds.get())
                .addAllWorkersFailed(job.failed());
        answer.forEach((key, value) -> reply.addPairs(Pair.newBuilder().setKey(key).setValue(value)));
        return reply.build();
    }

    // ------------------------------------------------------------- the rules

    /**
     * Sends the liveness RPC on a shorter deadline than a task call.
     * {@code RunJob} reaches it through {@link JobContext#probe} and determines
     * the policy for a missing reply.
     */
    boolean alive(String worker) {
        try {
            Alive alive = HeartbeatGrpc.newBlockingStub(channel(worker))
                    .withDeadlineAfter(PING_MS, TimeUnit.MILLISECONDS)
                    .ping(Beat.newBuilder().setFrom("master").build());
            return alive != null;
        } catch (StatusRuntimeException e) {
            return false;
        }
    }

    /**
     * Returns a channel to a Worker.
     *
     * <p>Before adoption, a peer has a host and port. After adoption, the
     * simulator locates a peer by the service it implements.
     *
     * @see GrpcMaster
     * @see DissalyMaster
     */
    abstract Channel channel(String address);

    /** Releases resources acquired by {@link #channel}. */
    public void close() {
    }

    /** The Master's counters, for a check to read. */
    public Map<String, Integer> counters() {
        var m = new LinkedHashMap<String, Integer>();
        m.put("mapTasksDone", mapTasksDone.get());
        m.put("reduceTasksDone", reduceTasksDone.get());
        return m;
    }
}
