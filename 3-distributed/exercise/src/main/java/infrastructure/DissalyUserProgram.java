package infrastructure;

import dissaly.api.Dissaly;
import dissaly.pb.Input;
import dissaly.pb.JobGrpc;
import dissaly.pb.Result;
import dissaly.pb.Workload;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mapreduce.pb.JobRequest;
import mapreduce.pb.JobResult;
import mapreduce.pb.MasterGrpc;
import mapreduce.pb.Pair;

/**
 * User Program for a simulated cluster. **Supplied code.**
 *
 * <p>This is {@link GrpcUserProgram} after adoption. The scenario declares and
 * starts the machines.
 *
 * <p><b>It does not compile before adoption.</b> {@code dissaly.api} and
 * {@code dissaly.pb} are unavailable to this build, which excludes this file.
 *
 * <h4>Adoption changes</h4>
 *
 * <p>The launcher methods and port constants are removed. The scenario's
 * {@code nodes:} block declares machines, including each machine's vCPUs, memory
 * cap and zone.
 *
 * <p>{@code runOneJob} becomes {@code Load} and {@code Run} on
 * {@code dissaly.Job}. The simulator measures {@code Run}.
 *
 * <h4>Load and Run</h4>
 *
 * {@code Load} runs off the clock. {@code Input.count} is the word count, and M
 * and R come from this service's own {@code config:} block. The payload is a
 * {@code JobRequest} that {@code Run} parses before its RPC.
 *
 * <p>M and R are not input. {@code input:} is the workload, and the engine
 * multiplies {@code count:} down a scale ladder; a task count scaled with it
 * would make two rungs two designs. They are written inside the {@code runs:}
 * entry that names this file, which is where the class that reads them is named.
 *
 * <p>{@code Run} receives a {@code Workload}, not {@code Input}, so it cannot
 * distinguish a probe run from a full run. This enables `scale:` projections.
 */
public final class DissalyUserProgram extends JobGrpc.JobImplBase {

    /** Output path, in the Task 2 format. */
    private static final Path OUT = Path.of("solution-wordcount.txt");

    /** Used when the scenario's config: block says nothing. */
    private static final int DEFAULT_MAP_TASKS = 21;
    private static final int DEFAULT_REDUCE_TASKS = 7;

    /** What the Master is given to finish the whole job. */
    private static final int JOB_MINUTES = 10;

    @Override public void load(Input in, StreamObserver<Workload> out) {
        var here = Dissaly.current();

        int mapTasks;
        int reduceTasks;
        try {
            mapTasks = Integer.parseInt(
                    here.config("mapTasks", String.valueOf(DEFAULT_MAP_TASKS)).trim());
            reduceTasks = Integer.parseInt(
                    here.config("reduceTasks", String.valueOf(DEFAULT_REDUCE_TASKS)).trim());
        } catch (NumberFormatException e) {
            out.onError(new IllegalStateException(
                    "mapTasks and reduceTasks are whole numbers, and this scenario's"
                    + " config: block says otherwise: " + e.getMessage(), e));
            return;
        }

        // Every Worker service joins the job. `--workers <n>` sets the pool size.
        List<String> workers = here.peersServing("mapreduce.Worker");
        if (workers.isEmpty()) {
            out.onError(new IllegalStateException("nobody serves mapreduce.Worker"));
            return;
        }

        long words = Math.max(1, in.getCount());
        JobRequest work = JobRequest.newBuilder()
                .setWords(words)
                .setSeed(here.seed())
                .setMapTasks(mapTasks)
                .setReduceTasks(reduceTasks)
                .addAllWorkers(workers)
                .build();

        out.onNext(Workload.newBuilder()
                .setCount(words)
                .setType(JobRequest.getDescriptor().getFullName())
                .setPayload(work.toByteString())
                .build());
        out.onCompleted();
    }

    @Override public void run(Workload work, StreamObserver<Result> out) {
        var here = Dissaly.current();

        JobRequest request;
        try {
            request = JobRequest.parseFrom(work.getPayload());
        } catch (Exception e) {
            out.onError(e);
            return;
        }

        // One word is one simulation unit, so `perUnit:` is priced per word.
        here.units(request.getWords());

        List<String> masters = here.peersServing("mapreduce.Master");
        if (masters.isEmpty()) {
            out.onError(new IllegalStateException("nobody serves mapreduce.Master"));
            return;
        }

        here.log(request.getWords() + " words, seed " + request.getSeed() + ", "
                + request.getMapTasks() + " map tasks, " + request.getReduceTasks()
                + " reduce tasks, " + request.getWorkersCount() + " workers");

        JobResult reply = MasterGrpc.newBlockingStub(here.channelTo(masters.get(0)))
                .withDeadlineAfter(JOB_MINUTES, TimeUnit.MINUTES)
                .runJob(request);

        long total = reply.getPairsList().stream().mapToLong(Pair::getValue).sum();

        try {
            // Declare bytes because DISSALy's disk model cannot observe a real write.
            here.wroteDisk(write(reply));
        } catch (IOException e) {
            out.onError(e);
            return;
        }

        // Checks compare the result total with the requested word count.
        here.reveal("rows", reply.getPairsCount());
        here.reveal("total", total);
        here.reveal("expected", request.getWords());

        // Round counts measure Master assignment. One task per free Worker yields
        // roughly M / n map rounds.
        here.reveal("mapRounds", reply.getMapRounds());
        here.reveal("reduceRounds", reply.getReduceRounds());

        if (total != request.getWords()) {
            here.log(total + " counted; " + request.getWords()
                    + " expected. Each input word must be counted once.");
        }

        out.onNext(Result.newBuilder()
                .putAnswer("rows", String.valueOf(reply.getPairsCount()))
                .putAnswer("total", String.valueOf(total))
                .putAnswer("expected", String.valueOf(request.getWords()))
                .putAnswer("mapTasks", String.valueOf(request.getMapTasks()))
                .putAnswer("reduceTasks", String.valueOf(request.getReduceTasks()))
                .putAnswer("workers", String.valueOf(request.getWorkersCount()))
                .putAnswer("mapRounds", String.valueOf(reply.getMapRounds()))
                .putAnswer("reduceRounds", String.valueOf(reply.getReduceRounds()))
                .putAnswer("tasksReexecuted", String.valueOf(reply.getTasksReexecuted()))
                .putAnswer("workersFailed", String.valueOf(reply.getWorkersFailedCount()))
                .build());
        out.onCompleted();
    }

    /** One key and its value per line, tab-separated, sorted by key. */
    private static long write(JobResult reply) throws IOException {
        var text = new StringBuilder();
        for (Pair p : reply.getPairsList()) {
            text.append(p.getKey()).append('\t').append(p.getValue()).append('\n');
        }
        Files.writeString(OUT, text.toString());
        return text.length();
    }
}
