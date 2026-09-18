package spot;

import dissaly.api.Dissaly;
import dissaly.api.DissalyCtx;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import spot.pb.Ack;
import spot.pb.CopyGrpc;
import spot.pb.Entry;
import spot.pb.Key;
import spot.pb.Step;

/**
 * Version 1
 */
public final class Kestrel extends CopyGrpc.CopyImplBase {

    /** Maximum time to wait for another replica. */
    private static final int PEER_MS = 150;

    private final Map<String, Long> value = new ConcurrentHashMap<>();
    private final Map<String, Long> version = new ConcurrentHashMap<>();

    /**
     * Every write, from the client and from another replica alike.
     * {@code step} says which it is, so the store needs no second RPC.
     */
    @Override public void write(Entry req, StreamObserver<Ack> out) {
        var here = Dissaly.current();
        here.units(1);

        if (req.getStep() == Step.STORE) {
            store(here, req, out);
            return;
        }

        long v = (long) here.clockMs();
        value.put(req.getKey(), req.getValue());
        version.put(req.getKey(), v);
        // The key alone, so the film's picker can put the held value on every
        // node's face and two replicas that disagree say so there.
        here.reveal(req.getKey(), req.getValue() + "  v" + v);
        here.reveal("last write", "took " + req.getValue()
                + " locally, now passing it on");

        Entry entry = Entry.newBuilder()
                .setKey(req.getKey()).setValue(req.getValue())
                .setVersion(v).setFrom(here.node()).setStep(Step.STORE).build();

        // Forward the write when a peer is reachable. The local write has
        // already succeeded, so a peer failure does not reject it.
        int held = 1;
        for (String peer : here.peersServing("Copy")) {
            try {
                CopyGrpc.newBlockingStub(here.channelTo(peer))
                        .withDeadlineAfter(PEER_MS, TimeUnit.MILLISECONDS)
                        .write(entry);
                held++;
            } catch (StatusRuntimeException e) {
                // Continue when this peer is unavailable.
            }
        }

        out.onNext(Ack.newBuilder().setFrom(here.node())
                .setApplied(true).setReplicas(held).build());
        out.onCompleted();
    }

    /** Stores a value received from another replica when its version is newer. */
    private void store(DissalyCtx here, Entry req, StreamObserver<Ack> out) {
        boolean newer = req.getVersion() >= version.getOrDefault(req.getKey(), 0L);
        if (newer) {
            value.put(req.getKey(), req.getValue());
            version.put(req.getKey(), req.getVersion());
            here.reveal(req.getKey(), req.getValue() + "  v" + req.getVersion());
            here.reveal("last write", "took " + req.getValue()
                    + ", passed on by " + req.getFrom());
        }
        out.onNext(Ack.newBuilder().setFrom(here.node()).setApplied(newer).setReplicas(1).build());
        out.onCompleted();
    }

    @Override public void read(Key req, StreamObserver<Entry> out) {
        var here = Dissaly.current();
        here.units(1);
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
