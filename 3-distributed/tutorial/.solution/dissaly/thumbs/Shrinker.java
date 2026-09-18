package thumbs;

import dissaly.api.Dissaly;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

/** gRPC handler for the dissaly tutorial stage. */
public final class Shrinker extends ShrinkerGrpc.ShrinkerImplBase {

    /** Thumbnails kept until the client calls {@code collect}. */
    private final List<Thumb> album = new ArrayList<>();

    private long totalBytes = 0;

    @Override public void thumbnail(Image request, StreamObserver<Thumb> out) {
        var here = Dissaly.current();

        byte[] pixels = request.getPixels().toByteArray();
        long ink = 0;
        for (byte b : pixels) ink += b & 0xFF;

        Thumb thumb = Thumb.newBuilder()
                .setId(request.getId())
                .setBytes(Math.max(1, pixels.length / 64))
                .setFrom(here.node())
                .build();

        synchronized (album) {
            album.add(thumb);
            totalBytes += thumb.getBytes();
        }

        // Report work in kilobytes so the scenario can price the input size.
        here.units(Math.max(1, pixels.length / 1024));

        // Add the retained thumbnail count to the trace.
        here.reveal("album", album.size() + " thumbnails");

        out.onNext(thumb);
        out.onCompleted();
    }

    @Override public void collect(Empty request, StreamObserver<Album> out) {
        synchronized (album) {
            out.onNext(Album.newBuilder()
                    .setFrom(Dissaly.current().node())
                    .addAllThumbs(album)
                    .setTotalBytes(totalBytes)
                    .build());
        }
        out.onCompleted();
    }
}
