package infrastructure;

import dissaly.api.Dissaly;
import io.grpc.Channel;

/**
 * Given Master <b>after adoption</b>. It asks the cluster for a Worker channel.
 *
 * <p>This is the simulated counterpart to {@link GrpcMaster}. The classes differ
 * only in channel lookup: the cluster resolves a Worker name and returns a
 * channel with the simulator interceptor.
 *
 * <p><b>It does not compile before adoption.</b> {@code dissaly.api} is absent
 * from this build's classpath, so `build.gradle` excludes this file and
 * {@link DissalyUserProgram}. Adoption replaces that build.
 *
 * <p>A scenario names this file rather than {@code Master.java}, because
 * {@code Master} is abstract and a scenario has to name something it can
 * construct:
 *
 * <pre>
 *   master:
 *     runs: { mapreduce.Master: src/infrastructure/DissalyMaster.java }
 * </pre>
 *
 * <p>The key is the schema service; the value is the implementing class.
 */
public final class DissalyMaster extends Master {

    /**
     * Does not cache channels. {@code channelTo} performs cluster lookup, and a
     * replacement Worker can reuse the same name. {@link GrpcMaster} caches its
     * own socket channels; the cluster owns these channels.
     */
    @Override Channel channel(String address) {
        return Dissaly.current().channelTo(address);
    }
}
