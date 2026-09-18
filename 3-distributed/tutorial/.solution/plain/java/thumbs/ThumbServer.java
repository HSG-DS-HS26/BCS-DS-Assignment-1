package thumbs;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** gRPC server for the standalone first stage. */
public class ThumbServer {

    private static final Logger logger = Logger.getLogger(ThumbServer.class.getName());

    private static final int PORT = 50052;

    private Server server;

    private void start() throws IOException {
        server = ServerBuilder.forPort(PORT)
                .addService(new Shrinker())
                .build()
                .start();
        logger.info("shrinker listening on " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("shutting down");
            try {
                ThumbServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    private void stop() throws InterruptedException {
        if (server != null) server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        var server = new ThumbServer();
        server.start();
        if (server.server != null) server.server.awaitTermination();
    }

    /** Handles thumbnail and collection requests. */
    static class Shrinker extends ShrinkerGrpc.ShrinkerImplBase {

        /** Thumbnails kept until the client calls {@code collect}. */
        private final List<Thumb> album = new ArrayList<>();

        private long totalBytes = 0;

        @Override public void thumbnail(Image request, StreamObserver<Thumb> out) {
            // Read the pixels so the work scales with the input size.
            byte[] pixels = request.getPixels().toByteArray();
            long ink = 0;
            for (byte b : pixels) ink += b & 0xFF;

            // Store a thumbnail with one sixty-fourth of the input size.
            Thumb thumb = Thumb.newBuilder()
                    .setId(request.getId())
                    .setBytes(Math.max(1, pixels.length / 64))
                    .setFrom("localhost:" + PORT)
                    .build();

            synchronized (album) {
                album.add(thumb);
                totalBytes += thumb.getBytes();
            }

            out.onNext(thumb);
            out.onCompleted();
        }

        @Override public void collect(Empty request, StreamObserver<Album> out) {
            synchronized (album) {
                out.onNext(Album.newBuilder()
                        .setFrom("localhost:" + PORT)
                        .addAllThumbs(album)
                        .setTotalBytes(totalBytes)
                        .build());
            }
            out.onCompleted();
        }
    }
}
