package infrastructure;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mapreduce.pb.JobRequest;
import mapreduce.pb.JobResult;
import mapreduce.pb.MasterGrpc;
import mapreduce.pb.Pair;

/**
 * User Program using real ports. **Supplied code.**
 *
 * <p>Before adoption, this class starts a Master and Workers in one JVM on local
 * ports, submits a job, writes the result, and exits.
 *
 * <pre>
 * ./gradlew run
 * ./gradlew run --args="20000 5 21 7 7"        words seed M R workers
 * </pre>
 *
 * <p>Every started machine remains available. Task 5 uses the simulator for
 * failures and slowdowns.
 *
 * <p>Servers use separate gRPC services and local ports. They do not share
 * application state; gRPC carries messages between them.
 *
 * <h4>After adoption</h4>
 *
 * <p>{@link DissalyUserProgram} is the adopted program:
 *
 * <ul>
 *   <li>The scenario's {@code nodes:} block replaces {@link #startMachines}.
 *       It declares machine resources and starts the machines.
 *   <li>{@link #runOneJob} becomes {@code Load} and {@code Run}; only
 *       {@code Run} is measured.
 *   <li>{@link #addresses} is replaced by querying the Worker service pool.
 * </ul>
 *
 * <p>After adoption, delete this class. Its {@code main} binds ports, which
 * {@code dissaly check} rejects in a lab.
 */
public final class GrpcUserProgram {

    /** Port the Master listens on. */
    public static final int MASTER_PORT = 50050;

    /** Port of the first Worker; the rest follow consecutively. */
    public static final int FIRST_WORKER_PORT = 50051;

    /** The task's Worker count. */
    public static final int WORKERS = 7;

    /** Output path, in the Task 2 format. */
    private static final Path OUT = Path.of("solution-wordcount.txt");

    public static void main(String[] args) throws IOException, InterruptedException {
        long words       = args.length > 0 ? Long.parseLong(args[0])   : 20_000;
        long seed        = args.length > 1 ? Long.parseLong(args[1])   : 5;
        int  mapTasks    = args.length > 2 ? Integer.parseInt(args[2]) : 21;
        int  reduceTasks = args.length > 3 ? Integer.parseInt(args[3]) : 7;

        // Number of Workers started by this launcher.
        int  workers     = args.length > 4 ? Integer.parseInt(args[4]) : WORKERS;

        var master = new GrpcMaster();
        List<Server> running = startMachines(master, workers);
        try {
            runOneJob(words, seed, mapTasks, reduceTasks, workers);
        } finally {
            master.close();
            running.forEach(Server::shutdownNow);
            for (Server s : running) s.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------- local cluster setup

    /**
     * Starts one Master and {@code workers} Workers, one port each.
     *
     * <p>A dissaly scenario declares and starts these machines after adoption.
     */
    private static List<Server> startMachines(Master master, int workers) throws IOException {
        var running = new ArrayList<Server>();

        running.add(ServerBuilder.forPort(MASTER_PORT).addService(master).build().start());
        System.out.println("Master on " + MASTER_PORT);

        // Each Worker exposes Worker and Heartbeat services in one process.
        for (int i = 0; i < workers; i++) {
            int port = FIRST_WORKER_PORT + i;
            running.add(ServerBuilder.forPort(port)
                    .addService(new mapreduce.Worker())
                    .addService(new mapreduce.Heartbeat())
                    .build().start());
            System.out.println("Worker on " + port);
        }

        System.out.println();
        return running;
    }

    /** Worker addresses used by the Master. */
    public static List<String> addresses(int workers) {
        var out = new ArrayList<String>(workers);
        for (int i = 0; i < workers; i++) out.add("localhost:" + (FIRST_WORKER_PORT + i));
        return out;
    }

    // -------------------------------------------------------- job submission

    /**
     * Submits one job, waits for it, and writes the result.
     */
    private static void runOneJob(long words, long seed, int mapTasks, int reduceTasks, int workers)
            throws IOException, InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("localhost:" + MASTER_PORT).usePlaintext().build();
        try {
            System.out.println(words + " words, seed " + seed + ", " + mapTasks + " map tasks, "
                    + reduceTasks + " reduce tasks, " + workers + " workers");

            long began = System.nanoTime();
            JobResult reply;
            try {
                reply = MasterGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(10, TimeUnit.MINUTES)
                        .runJob(JobRequest.newBuilder()
                                .setWords(words).setSeed(seed)
                                .setMapTasks(mapTasks).setReduceTasks(reduceTasks)
                                .addAllWorkers(addresses(workers))
                                .build());
            } catch (StatusRuntimeException e) {
                // Print Master status; its log contains task details.
                System.out.println();
                System.out.println("job did not finish: " + e.getStatus().getCode());
                System.out.println(e.getStatus().getDescription());
                System.out.println();
                System.out.println("See the Master's log for task details.");
                return;
            }
            long tookMs = (System.nanoTime() - began) / 1_000_000;

            write(reply);

            long total = reply.getPairsList().stream().mapToLong(Pair::getValue).sum();
            System.out.println();
            System.out.println(reply.getPairsCount() + " distinct words, " + total
                    + " in total, in " + tookMs + " ms");
            System.out.println(reply.getMapRounds() + " rounds of map tasks, "
                    + reply.getReduceRounds() + " of reduce tasks");
            System.out.println(reply.getTasksReexecuted() + " tasks re-executed"
                    + (reply.getWorkersFailedCount() == 0
                            ? "" : ", Workers failed: " + reply.getWorkersFailedList()));
            System.out.println("written to " + OUT.toAbsolutePath());

            if (total != words) {
                System.out.println();
                System.out.println(total + " counted; expected " + words + ".");
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** One key and its value per line, tab-separated, sorted by key. */
    private static void write(JobResult reply) throws IOException {
        var text = new StringBuilder();
        for (Pair p : reply.getPairsList()) {
            text.append(p.getKey()).append('\t').append(p.getValue()).append('\n');
        }
        Files.writeString(OUT, text.toString(), StandardCharsets.UTF_8);
    }
}
