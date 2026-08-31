//package docs.cameraAndAttachments;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.io.InputStream;
//
//@Service
//@RequiredArgsConstructor
//public class ObjectStorage implements MediaStorage {
//
//    private final MinioClient minioClient;
//
//    @Value("${minio.bucket}")
//    private String bucket;
//
//    @Override
//    public void store(
//            String key,
//            InputStream inputStream,
//            long size,
//            String contentType
//    ) {
//        try {
//            minioClient.putObject(
//                    PutObjectArgs.builder()
//                            .bucket(bucket)
//                            .object(key)
//                            .stream(inputStream, size, -1)
//                            .contentType(contentType)
//                            .build()
//            );
//        } catch (Exception e) {
//            throw new IllegalStateException(
//                    "Failed to store media object: " + key,
//                    e
//            );
//        }
//    }
//
//    @Override
//    public InputStream retrieve(String key) {
//        try {
//            return minioClient.getObject(
//                    GetObjectArgs.builder()
//                            .bucket(bucket)
//                            .object(key)
//                            .build()
//            );
//        } catch (Exception e) {
//            throw new IllegalStateException(
//                    "Failed to retrieve media object: " + key,
//                    e
//            );
//        }
//    }
//
//    @Override
//    public void delete(String key) {
//        try {
//            minioClient.removeObject(
//                    RemoveObjectArgs.builder()
//                            .bucket(bucket)
//                            .object(key)
//                            .build()
//            );
//        } catch (Exception e) {
//            throw new IllegalStateException(
//                    "Failed to delete media object: " + key,
//                    e
//            );
//        }
//    }
//
//    @Override
//    public boolean exists(String key) {
//        try {
//            minioClient.statObject(
//                    StatObjectArgs.builder()
//                            .bucket(bucket)
//                            .object(key)
//                            .build()
//            );
//
//            return true;
//
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}