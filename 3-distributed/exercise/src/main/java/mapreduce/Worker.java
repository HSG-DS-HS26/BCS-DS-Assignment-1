package mapreduce;

// Uncomment the imports used by this implementation. Generated mapreduce.pb
// classes become available after worker.proto declares their messages.
//
// import infrastructure.Corpus;
// import io.grpc.Status;
// import io.grpc.stub.StreamObserver;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.TreeMap;
// import java.util.concurrent.TimeUnit;
// import mapreduce.pb.MapDone;
// import mapreduce.pb.MapTask;
// import mapreduce.pb.Pair;
// import mapreduce.pb.ReduceDone;
// import mapreduce.pb.ReduceTask;
// import mapreduce.pb.ShuffleReply;
// import mapreduce.pb.ShuffleRequest;
// import mapreduce.pb.WorkerGrpc;

/**
 * =============================================================== provided
 *
 * This class extends {@code infrastructure.Shuffle}. It stores intermediate
 * pairs, partitions map output, answers Fetch, and provides channels to peers.
 *
 * <pre>
 * store(task, piece, reduceTasks)  count a piece and store its groups
 * group(task, partition)           return one group or null
 * channel(address)                 return a reusable peer channel
 * FETCH_MS                         deadline for Fetch
 * </pre>
 *
 * {@code infrastructure.GrpcUserProgram} constructs Worker with no arguments.
 * Do not add a constructor.
 *
 * <p>=========================================================== student work
 *
 * <p>TODO(student): Extend the generated Worker base class and implement
 * RunMapTask and RunReduceTask after declaring them in {@code worker.proto}.
 *
 * <p>TODO(student): The map handler generates its input locally with
 * {@code infrastructure.Corpus.piece(...)} and stores its output with
 * {@code store}. Use the request's task index and job parameters. The input
 * piece stays local to the Worker.
 *
 * <p>TODO(student): The reduce handler collects its partition from every map
 * task through Fetch, combines equal keys, and returns sorted output.
 *
 * <p>Implementation pitfalls:
 *
 * <ul>
 *   <li>Fetch by map-task index. A reply from one holder does not cover another
 *       map task's output.</li>
 *   <li>Apply {@code FETCH_MS} to each Fetch call. Do not wait indefinitely for
 *       an unreachable holder.</li>
 *   <li>Treat {@code found = false} as a reachable Worker that does not hold
 *       the task. Use an RPC failure for an unreachable Worker.</li>
 *   <li>Fail the reduce RPC if any required group cannot be collected. Returning
 *       partial output lets the Master add an incomplete result.</li>
 *   <li>Complete the response observer exactly once: send a successful reply
 *       and complete it, or report the failure.</li>
 * </ul>
 */
public final class Worker extends infrastructure.Shuffle {

    // TODO(student): Override runMapTask and runReduceTask after worker.proto
    // declares them. Until then calls fail with UNIMPLEMENTED.
}
