package thumbs;

import com.google.protobuf.ByteString;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Standalone client for the first tutorial stage. */
public class ThumbClient {

    private static final Logger logger = Logger.getLogger(ThumbClient.class.getName());

    private static final String TARGET = "localhost:50052";

    /** Pixel bytes sent for each image. */
    private static final int PIXELS = 64 * 1024;

    /** Images sent during the standalone run. */
    private static final int IMAGES = 40;

    public static void main(String[] args) throws Exception {
        ManagedChannel channel = Grpc.newChannelBuilder(TARGET, InsecureChannelCredentials.create())
                .build();
        try {
            var stub = ShrinkerGrpc.newBlockingStub(channel);

            byte[] buf = new byte[PIXELS];
            new Random(1).nextBytes(buf);
            ByteString pixels = ByteString.copyFrom(buf);

            // Send the configured images to the server.
            for (int i = 0; i < IMAGES; i++) {
                stub.thumbnail(Image.newBuilder()
                        .setId("img" + i)
                        .setPixels(pixels)
                        .build());
            }

            // Request the thumbnails that the server retained.
            Album album = stub.collect(Empty.getDefaultInstance());

            logger.info(IMAGES + " images -> " + album.getThumbsCount()
                        + " thumbnails from " + album.getFrom()
                        + ", " + album.getTotalBytes() + " bytes");
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
