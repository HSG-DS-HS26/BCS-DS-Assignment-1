package spot;

import dissaly.api.Dissaly;
import dissaly.pb.Input;
import dissaly.pb.JobGrpc;
import dissaly.pb.Result;
import dissaly.pb.Workload;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import spot.pb.CopyGrpc;
import spot.pb.Entry;
import spot.pb.Key;
import spot.pb.Plan;

/**
 * Implements {@code dissaly.Job}, the service dissaly calls to start the work.
 * Each round writes {@code x=n}, reads {@code x} from every replica, and records
 * the replies.
 */
public final class Client extends JobGrpc.JobImplBase {

    /** Maximum wait for a replica before recording a failed write. */
    private static final int PATIENCE_MS = 400;

    /** Reference-time delay between rounds. */
    private static final double GAP_REF_MS = 250;

    /** Key used by the workload. */
    private static final String KEY = "x";

    /** Builds the workload before measurement using the scenario's {@code count}. */
    @Override public void load(Input in, StreamObserver<Workload> out) {
        Plan plan = Plan.newBuilder()
                .setRounds((int) in.getCount())
                .setKey(KEY)
                .build();
        out.onNext(Workload.newBuilder()
                .setCount(plan.getRounds())
                .setType(Plan.getDescriptor().getFullName())
                .setPayload(plan.toByteString())
                .build());
        out.onCompleted();
    }

    /** Runs the simulation and reports the result after all rounds finish. */
    @Override public void run(Workload work, StreamObserver<Result> out) {
        var here = Dissaly.current();
        Plan plan;
        try {
            plan = Plan.parseFrom(work.getPayload());
        } catch (Exception e) {
            out.onError(e);
            return;
        }

        // Replicas remain alive during the run; only their network links change.
        List<String> replicas = new ArrayList<>(here.peersServing("Copy"));
        if (replicas.isEmpty()) {
            out.onError(new IllegalStateException("nobody serves Copy"));
            return;
        }

        int accepted = 0;
        int refused = 0;
        int agreed = 0;
        int disagreed = 0;

        for (int round = 1; round <= plan.getRounds(); round++) {
            here.units(1);

            // Distribute writes across replicas.
            String target = replicas.get(round % replicas.size());
            if (write(plan.getKey(), target, round)) accepted++; else refused++;

            // Read the key from every replica and record the results together.
            var rows = new LinkedHashMap<String, String>();
            for (String replica : replicas) rows.put(replica, read(plan.getKey(), replica));
            rows.forEach(here::reveal);

            // Exclude missing replies from the value comparison.
            var opinions = new TreeSet<>(rows.values());
            opinions.removeIf(line -> line.startsWith("("));
            if (opinions.size() > 1) {
                disagreed++;
                here.log("round " + round + "   replicas returned different values: " + opinions);
            } else if (opinions.size() == 1) {
                agreed++;
            }

            here.sleep(GAP_REF_MS);
        }

        out.onNext(Result.newBuilder()
                .putAnswer("rounds", String.valueOf(plan.getRounds()))
                .putAnswer("writesAccepted", String.valueOf(accepted))
                .putAnswer("writesRefused", String.valueOf(refused))
                .putAnswer("roundsAllReplicasAgreed", String.valueOf(agreed))
                .putAnswer("roundsReplicasDisagreed", String.valueOf(disagreed))
                .build());
        out.onCompleted();
    }

    /** Sends {@code WRITE key=value} and returns whether the replica accepted it. */
    private boolean write(String key, String target, long value) {
        var here = Dissaly.current();
        try {
            var ack = CopyGrpc.newBlockingStub(here.channelTo(target))
                    .withDeadlineAfter(PATIENCE_MS, TimeUnit.MILLISECONDS)
                    .write(Entry.newBuilder().setKey(key).setValue(value).build());
            here.reveal("wrote", "WRITE " + key + "=" + value + " -> " + target
                    + "  ACCEPTED  (" + ack.getReplicas() + " replicas hold it)");
            return true;
        } catch (StatusRuntimeException e) {
            here.reveal("wrote", "WRITE " + key + "=" + value + " -> " + target
                    + "  REFUSED  (" + e.getStatus().getCode() + ": "
                    + e.getStatus().getDescription() + ")");
            return false;
        }
    }

    /** Reads the key from one replica and formats its response. */
    private String read(String key, String replica) {
        var here = Dissaly.current();
        try {
            Entry got = CopyGrpc.newBlockingStub(here.channelTo(replica))
                    .withDeadlineAfter(PATIENCE_MS, TimeUnit.MILLISECONDS)
                    .read(Key.newBuilder().setKey(key).build());
            return got.getFound()
                    ? key + "=" + got.getValue() + "  v" + got.getVersion()
                    : "(nothing here yet)";
        } catch (StatusRuntimeException e) {
            return "(no answer: " + e.getStatus().getCode() + ")";
        }
    }
}
