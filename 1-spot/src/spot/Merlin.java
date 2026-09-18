package spot;

import dissaly.api.Dissaly;
import dissaly.api.DissalyCtx;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import spot.pb.Ack;
import spot.pb.CopyGrpc;
import spot.pb.Entry;
import spot.pb.Key;
import spot.pb.Step;

/**
 * Another variant
 */
public final class Merlin extends CopyGrpc.CopyImplBase {

    /** Maximum wait for each backup. */
    private static final int PEER_MS = 150;

    /** Maximum wait for the primary, including its backup calls. */
    private static final int FORWARD_MS = 350;

    /** Values returned by reads on this replica. */
    private final Map<String, Long> value = new ConcurrentHashMap<>();
    private final Map<String, Long> version = new ConcurrentHashMap<>();

    /** Values staged by the primary and waiting for commit. */
    private final Map<String, Entry> staged = new ConcurrentHashMap<>();

    /**
     * Replicas known to this node, including itself.
     */
    private final Set<String> known = new TreeSet<>();

    /**
     * Selects the primary by name. Every replica applies the same rule locally.
     */
    private String primary(DissalyCtx here) {
        known.add(here.node());
        known.addAll(here.peersServing("Copy"));
        return known.iterator().next();
    }

    /** Returns every known replica except this node. */
    private List<String> backups(DissalyCtx here) {
        var out = new ArrayList<>(known);
        out.remove(here.node());
        return out;
    }

    /** Handles client writes and the primary's staging and commit messages. */
    @Override public void write(Entry req, StreamObserver<Ack> out) {
        var here = Dissaly.current();
        here.units(1);

        switch (req.getStep()) {
            case STAGE -> stage(here, req, out);
            case COMMIT -> commit(here, req, out);
            default -> fromClient(here, req, out);
        }
    }

    /** Handles a client write on the primary. */
    private void fromClient(DissalyCtx here, Entry req, StreamObserver<Ack> out) {
        String primary = primary(here);
        if (!primary.equals(here.node())) {
            forward(here, primary, req, out);
            return;
        }

        long v = (long) here.clockMs();

        // Require every known backup, including unreachable backups.
        List<String> backups = backups(here);
        Entry entry = Entry.newBuilder()
                .setKey(req.getKey()).setValue(req.getValue())
                .setVersion(v).setFrom(here.node()).setStep(Step.STAGE).build();

        // Stage the value on each backup without exposing it to reads.
        var took = new ArrayList<String>();
        for (String backup : backups) {
            try {
                CopyGrpc.newBlockingStub(here.channelTo(backup))
                        .withDeadlineAfter(PEER_MS, TimeUnit.MILLISECONDS)
                        .write(entry);
                took.add(backup);
            } catch (StatusRuntimeException e) {
                // An unavailable backup prevents the write from being committed.
            }
        }

        if (took.size() < backups.size()) {
            // A refused write leaves this replica's current value unchanged.
            here.reveal("last write", "refused " + req.getValue() + ": "
                    + took.size() + " of " + backups.size() + " backups took it");
            out.onError(Status.UNAVAILABLE.withDescription(
                    took.size() + " of " + backups.size() + " backups").asRuntimeException());
            return;
        }

        // Commit the value after every backup has staged it.
        value.put(req.getKey(), req.getValue());
        version.put(req.getKey(), v);
        // The backup already holds the entry, but the version identifies the
        // write in the run.
        Entry commit = Entry.newBuilder()
                .setKey(req.getKey()).setVersion(v)
                .setFrom(here.node()).setStep(Step.COMMIT).build();
        for (String backup : took) {
            try {
                CopyGrpc.newBlockingStub(here.channelTo(backup))
                        .withDeadlineAfter(PEER_MS, TimeUnit.MILLISECONDS)
                        .write(commit);
            } catch (StatusRuntimeException e) {
                // The write is already committed, so this acknowledgement is
                // not retried.
            }
        }

        here.reveal(req.getKey(), req.getValue() + "  v" + v);
        here.reveal("last write", "committed " + req.getValue() + " on all "
                + (backups.size() + 1) + " replicas");
        out.onNext(Ack.newBuilder().setFrom(here.node())
                .setApplied(true).setReplicas(backups.size() + 1).build());
        out.onCompleted();
    }

    /** Forwards a client write to the primary. */
    private void forward(DissalyCtx here, String primary, Entry req, StreamObserver<Ack> out) {
        try {
            Ack ack = CopyGrpc.newBlockingStub(here.channelTo(primary))
                    .withDeadlineAfter(FORWARD_MS, TimeUnit.MILLISECONDS)
                    .write(req);
            out.onNext(ack);
            out.onCompleted();
        } catch (StatusRuntimeException e) {
            // Preserve the failure and leave the write uncommitted.
            here.reveal("last write", "refused " + req.getValue()
                    + ": " + primary + " said " + e.getStatus().getCode());
            out.onError(Status.UNAVAILABLE.withDescription(
                    "the primary " + primary + " did not take it ("
                    + e.getStatus().getCode() + ")").asRuntimeException());
        }
    }

    /** Stages a value received from the primary without exposing it to reads. */
    private void stage(DissalyCtx here, Entry req, StreamObserver<Ack> out) {
        staged.put(req.getKey(), req);
        out.onNext(Ack.newBuilder().setFrom(here.node()).setApplied(false).setReplicas(1).build());
        out.onCompleted();
    }

    /** Commits a staged value after all replicas acknowledge it. */
    private void commit(DissalyCtx here, Entry req, StreamObserver<Ack> out) {
        Entry held = staged.remove(req.getKey());
        if (held != null) {
            value.put(held.getKey(), held.getValue());
            version.put(held.getKey(), held.getVersion());
            here.reveal(held.getKey(), held.getValue() + "  v" + held.getVersion());
            here.reveal("last write", "committed " + held.getValue()
                    + ", told by " + held.getFrom());
        }
        out.onNext(Ack.newBuilder().setFrom(here.node()).setApplied(held != null).setReplicas(1).build());
        out.onCompleted();
    }

    /**
     * Reads go through the primary so a backup does not return a stale value.
     */
    @Override public void read(Key req, StreamObserver<Entry> out) {
        var here = Dissaly.current();
        here.units(1);

        String primary = primary(here);
        if (!primary.equals(here.node())) {
            try {
                out.onNext(CopyGrpc.newBlockingStub(here.channelTo(primary))
                        .withDeadlineAfter(FORWARD_MS, TimeUnit.MILLISECONDS)
                        .read(req));
                out.onCompleted();
            } catch (StatusRuntimeException e) {
                out.onError(Status.UNAVAILABLE.withDescription(
                        "cannot reach the primary " + primary + ", so this replica"
                        + " does not know whether what it holds is still the latest")
                        .asRuntimeException());
            }
            return;
        }

        Long held = value.get(req.getKey());
        out.onNext(Entry.newBuilder()
                .setKey(req.getKey())
                .setValue(held == null ? 0L : held)
                .setVersion(version.getOrDefault(req.getKey(), 0L))
                .setFrom(here.node())
                .setFound(held != null)
                .build());
        out.onCompleted();
    }
}
