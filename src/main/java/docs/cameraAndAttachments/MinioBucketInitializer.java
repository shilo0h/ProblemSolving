package docs.cameraAndAttachments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class MinioBucketInitializer implements CommandLineRunner {
//
////    private final MinioClient minioClient;
//
//    @Value("${minio.bucket}")
//    private String bucket;
//
////    @Override
////    public void run(String... args) {
////        try {
////            boolean exists = minioClient.bucketExists(
////                    BucketExistsArgs.builder()
////                            .bucket(bucket)
////                            .build()
////            );
////
////            if (!exists) {
////                minioClient.makeBucket(
////                        MakeBucketArgs.builder()
////                                .bucket(bucket)
////                                .build()
////                );
////
////                log.info("Created MinIO bucket: {}", bucket);
////            } else {
////                log.info("MinIO bucket already exists: {}", bucket);
////            }
////
////        } catch (Exception e) {
////            log.warn(
////                    "MinIO is unavailable. Skipping bucket initialization for '{}'. " +
////                            "The application will continue without attachment storage.",
////                    bucket,
////                    e
////            );
////        }
////    }
//}