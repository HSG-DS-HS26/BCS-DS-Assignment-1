package thumbs;

import com.google.protobuf.ByteString;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Job for the dissaly tutorial stage. It implements {@code dissaly.Job}, the
 * service dissaly calls to start the work.
 *
 * <p>Stage 1 used `main` and a fixed count of forty images. Here, `Load` builds
 * the input before measurement and `Run` performs the measured work.
 */
public final class ThumbJob extends JobGrpc.JobImplBase {

    /** Maximum wait for each RPC. */
    private static final int PATIENCE_MS = 400;

    /** Number of pixel bytes in each image. */
    private static final int PIXEL_BYTES = 65536;

    /**
     * Builds pixels before measurement, so creation time is excluded from the
     * simulated job.
     *
     * <p>The simulation has no `source:` entry, so the seed generates the
     * workload. The same seed reproduces the input; a sweep changes the seed.
     */
    @Override public void load(Input in, StreamObserver<Workload> out) {
        byte[] buf = new byte[PIXEL_BYTES];
        new Random(Dissaly.current().seed()).nextBytes(buf);

        Split split = Split.newBuilder()
                .setImages((int) in.getCount())
                .setPixels(ByteString.copyFrom(buf))
                .build();

        out.onNext(Workload.newBuilder()
                .setCount(split.getImages())
                .setType(Split.getDescriptor().getFullName())
                .setPayload(split.toByteString())
                .build());
        out.onCompleted();
    }

    /** Runs the simulation workload and reports its result. */
    @Override public void run(Workload work, StreamObserver<Result> out) {
        var here = Dissaly.current();
        Split split;
        try {
            split = Split.parseFrom(work.getPayload());
        } catch (Exception e) {
            out.onError(e);
            return;
        }
        final int images = split.getImages();
        final ByteString pixels = split.getPixels();

        List<String> peers = here.peersServing("Shrinker");
        here.log(images + " images across " + peers);

        var stubs = new LinkedHashMap<String, ShrinkerGrpc.ShrinkerBlockingStub>();
        for (String peer : peers) stubs.put(peer, ShrinkerGrpc.newBlockingStub(here.channelTo(peer)));

        var strikes = new LinkedHashMap<String, Integer>();
        for (String peer : peers) strikes.put(peer, 0);
        int lost = 0;

        for (int i = 0; i < images; i++) {
            // Distribute calls in round-robin order.
            String peer = peers.get(i % peers.size());

            // Stop sending calls to a peer after three failures.
            if (strikes.get(peer) >= 3) { lost++; continue; }

            try {
                stubs.get(peer).withDeadlineAfter(PATIENCE_MS, TimeUnit.MILLISECONDS)
                        .thumbnail(Image.newBuilder().setId("img" + i).setPixels(pixels).build());
                strikes.put(peer, 0);
            } catch (StatusRuntimeException e) {
                strikes.merge(peer, 1, Integer::sum);
                lost++;
            }
        }

        // Collect retained thumbnails from each peer.
        var summary = new ArrayList<String>();
        long collected = 0;
        long bytes = 0;
        for (String peer : peers) {
            if (strikes.get(peer) >= 3) { summary.add(peer + " gone"); continue; }
            try {
                Album album = stubs.get(peer).withDeadlineAfter(PATIENCE_MS, TimeUnit.MILLISECONDS)
                        .collect(Empty.getDefaultInstance());
                collected += album.getThumbsCount();
                bytes += album.getTotalBytes();
                summary.add(peer + " " + album.getThumbsCount());
            } catch (StatusRuntimeException e) {
                summary.add(peer + " no answer");
            }
        }

        out.onNext(Result.newBuilder()
                .putAnswer("thumbnails", String.valueOf(collected))
                .putAnswer("images", String.valueOf(images))
                .putAnswer("bytes", String.valueOf(bytes))
                .putAnswer("lost", String.valueOf(lost))
                .putAnswer("perNode", String.join("  ", summary))
                .build());
        out.onCompleted();
    }
}
