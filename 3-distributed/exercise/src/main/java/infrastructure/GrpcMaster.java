package infrastructure;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Given Master <b>before adoption</b>. It dials a Worker directly.
 *
 * <p>{@link Master} handles both phases, the heartbeat, and counters. This
 * subclass supplies Worker channels for a program using real host-and-port
 * sockets.
 *
 * <p>This class pairs with {@link GrpcUserProgram}, which starts it. Its
 * counterpart is {@link DissalyMaster}.
 *
 * <p><b>Delete this file after adoption</b>, together with
 * {@code GrpcUserProgram.java}. Adoption removes {@code grpc-netty-shaded}, so
 * these socket channels cannot run in the simulated cluster.
 */
public final class GrpcMaster extends Master {

    /** One reusable channel per Worker. */
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @Override Channel channel(String address) {
        return channels.computeIfAbsent(address, a ->
                ManagedChannelBuilder.forTarget(a).usePlaintext().build());
    }

    /** Closes the channels opened by this Master. */
    @Override public void close() {
        channels.values().forEach(ManagedChannel::shutdownNow);
        channels.clear();
    }
}
