package mapreduce;

// Uncomment the imports used by this implementation. Generated mapreduce.pb
// classes become available after worker.proto declares Heartbeat and its
// messages.
//
// import io.grpc.stub.StreamObserver;
// import mapreduce.pb.Alive;
// import mapreduce.pb.Beat;
// import mapreduce.pb.HeartbeatGrpc;

/**
 * =============================================================== provided
 *
 * {@code infrastructure.GrpcUserProgram} installs this service beside
 * {@link Worker}. {@code infrastructure.Master} sends Ping after a task misses
 * its deadline. The service and message names are listed in {@code worker.proto}.
 *
 * <p>Ping has a 1,000 ms deadline; tasks have a 20,000 ms deadline. Heartbeat
 * and Worker share a process and its thread pool, which can be occupied by
 * shuffle reads during Reduce.
 *
 * <p>=========================================================== student work
 *
 * <p>TODO(student): Extend the generated Heartbeat base class and implement
 * Ping. Decide what the reply may inspect and document what a successful reply
 * guarantees. {@link RunJob#writeOff} relies on that guarantee.
 */
public final class Heartbeat /* extends the base class generated from worker.proto */ {
}
