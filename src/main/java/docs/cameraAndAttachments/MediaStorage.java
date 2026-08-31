package docs.cameraAndAttachments;

import java.io.InputStream;

public interface MediaStorage {

    /*
        We define this so we just know what we want now how they are implemented
        if tomorrow the implementation changes we don't have to touch the service that
        use the functions
     */

    void store(
            String key,
            InputStream inputStream,
            long size,
            String contentType
    );

    InputStream retrieve(String key);

    void delete(String key);

    boolean exists(String key);
}