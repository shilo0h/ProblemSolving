//package docs.cameraAndAttachments;
//
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.Set;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class MediaService {
//
//    private final MediaRepository mediaRepository;
//    private final MediaChecksumService mediaChecksumService;
//    private final MediaStorage mediaStorage;
//    private final MediaThumbnailService mediaThumbnailService;
//    private final ChatroomService chatroomService;
//    private final MediaMapper mediaMapper;
//
//    private final Tika tika = new Tika();
//
//    public List<MediaDTO> addMedia(Long ownerId, Long chatroomId, List<MultipartFile> files) throws IOException {
//        if (files == null || files.isEmpty()) {
//            throw new AppException(ErrorCode.MEDIA_EMPTY, "No files provided");
//        }
//
//        if (files.size() > 20) {
//            throw new AppException(ErrorCode.BAD_REQUEST, "A message cannot contain more than 20 attachments");
//        }
//
//        List<Media> savedMedia = new ArrayList<>();
//        for (MultipartFile file : files) {
//            Media media = addSingleMedia(ownerId, chatroomId, file);
//            savedMedia.add(media);
//        }
//
//        return savedMedia.stream()
//                .map(mediaMapper::toDTO)
//                .toList();
//    }
//
//    private Media addSingleMedia(Long ownerId, Long chatroomId, MultipartFile file) throws IOException {
//
//        validateFile(file);
//
//        byte[] fileBytes = file.getBytes();
//
//        String detectedContentType = tika.detect(fileBytes);
//
//        MediaKind kind = determineMediaKind(detectedContentType);
//
//        String checksum = mediaChecksumService.calculateSha256(fileBytes);
//
//        // Check if this exact file already exists
//        Optional<Media> existingMedia = mediaRepository.findByChatroomIdAndChecksumSha256(chatroomId,checksum);
//
//        if (existingMedia.isPresent()) {
//
//            Media existing = existingMedia.get();
//
//            log.info("Duplicate media detected: checksum={}, mediaId={}", checksum, existing.getId());
//
//            return existing;
//        }
//
//        String storageKey = generateStorageKey(chatroomId, checksum, detectedContentType);
//
//        String originalFilename = file.getOriginalFilename();
//
//        if (originalFilename == null || originalFilename.isBlank()) {
//            originalFilename = "file";
//        }
//
//        // Create database record
//        Media media = new Media();
//
//        media.setOwnerId(ownerId);
//        media.setChatroomId(chatroomId);
//        media.setKind(kind);
//        media.setContentType(detectedContentType);
//        media.setSizeBytes((long) fileBytes.length);
//        media.setOriginalFilename(originalFilename);
//        media.setChecksumSha256(checksum);
//        media.setStorageKey(storageKey);
//        media.setState(MediaState.PENDING);
//
//        /*
//         * Generate thumbnail only for images.
//         */
//        MediaThumbnailService.ThumbnailResult thumbnail = null;
//
//        if (kind == MediaKind.IMAGE) {
//
//            thumbnail = mediaThumbnailService.generateThumbnail(fileBytes, detectedContentType);
//
//            media.setWidth(thumbnail.width());
//            media.setHeight(thumbnail.height());
//
//            String thumbnailKey = generateThumbnailKey(chatroomId,checksum, detectedContentType);
//
//            media.setThumbnailKey(thumbnailKey);
//        }
//
//        media = mediaRepository.save(media);
//
//        try {
//
//            /*
//             * Upload original file.
//             */
//            mediaStorage.store(storageKey, new ByteArrayInputStream(fileBytes), fileBytes.length, detectedContentType);
//
//            /*
//             * Upload thumbnail if this is an image.
//             */
//            if (thumbnail != null) {
//
//                byte[] thumbnailBytes = thumbnail.bytes();
//
//                mediaStorage.store(media.getThumbnailKey(), new ByteArrayInputStream(thumbnailBytes), thumbnailBytes.length, detectedContentType);
//            }
//
//            /*
//             * Everything succeeded.
//             */
//            media.setState(MediaState.READY);
//
//            mediaRepository.save(media);
//
//            log.info("Media uploaded successfully: mediaId={}, storageKey={}", media.getId(), storageKey);
//
//            return media;
//
//        } catch (Exception e) {
//
//            log.error("Failed to upload media: mediaId={}, storageKey={}", media.getId(), storageKey, e);
//
//            /*
//             * Cleanup original object if it was uploaded.
//             */
//            try {
//
//                if (mediaStorage.exists(storageKey)) {
//                    mediaStorage.delete(storageKey);
//                }
//
//                if (media.getThumbnailKey() != null && mediaStorage.exists(media.getThumbnailKey())) {
//
//                    mediaStorage.delete(media.getThumbnailKey());
//                }
//
//            } catch (Exception cleanupException) {
//
//                log.error("Failed to cleanup media objects: mediaId={}", media.getId(), cleanupException);
//            }
//
//            media.setState(MediaState.FAILED);
//
//            mediaRepository.save(media);
//
//            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Failed to store media", e);
//        }
//    }
//
//    public InputStream downloadMedia(Long mediaId, Long userId) {
//
//        Media media = getMedia(mediaId);
//
//        if (media.getState() != MediaState.READY) {
//            throw new AppException(
//                    ErrorCode.BAD_REQUEST,
//                    "Media is not available"
//            );
//        }
//
//        if (!chatroomService.isUserMemberOfChatroom(
//                media.getChatroomId(),
//                userId
//        )) {
//            throw new AppException(
//                    ErrorCode.AUTH_FORBIDDEN,
//                    "You do not have access to this media"
//            );
//        }
//
//        return mediaStorage.retrieve(media.getStorageKey());
//    }
//
//    public InputStream downloadThumbnail(Long mediaId, Long userId) {
//
//        Media media = getMedia(mediaId);
//
//        if (media.getState() != MediaState.READY) {
//            throw new AppException(
//                    ErrorCode.BAD_REQUEST,
//                    "Media is not available"
//            );
//        }
//
//        if (!chatroomService.isUserMemberOfChatroom(
//                media.getChatroomId(),
//                userId
//        )) {
//            throw new AppException(
//                    ErrorCode.AUTH_FORBIDDEN,
//                    "You do not have access to this media"
//            );
//        }
//
//        if (media.getThumbnailKey() == null) {
//            throw new AppException(
//                    ErrorCode.NOT_FOUND,
//                    "Thumbnail not available for this media"
//            );
//        }
//
//        return mediaStorage.retrieve(media.getThumbnailKey());
//    }
//
//
//    public Media getMedia(Long mediaId) {
//
//        return mediaRepository.findById(mediaId)
//                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Media not found"));
//    }
//
//
//    private MediaKind determineMediaKind(String contentType) {
//
//        if (contentType.startsWith("image/")) {
//            return MediaKind.IMAGE;
//        }
//
//        return MediaKind.DOCUMENT;
//    }
//
//    private String generateStorageKey(Long chatroomId, String checksum, String contentType) {
//        String extension = getExtension(contentType);
//
//        return "chatrooms/"
//                + chatroomId
//                + "/media/"
//                + checksum
//                + "/original"
//                + extension;
//    }
//
//
//    private String generateThumbnailKey(Long chatroomId, String checksum, String contentType) {
//
//        String extension = getExtension(contentType);
//
//        return "chatrooms/"
//                + chatroomId
//                + "/media/"
//                + checksum
//                + "/thumbnail"
//                + extension;
//    }
//
//
//    private String getExtension(String contentType) {
//
//        return switch (contentType) {
//
//            case "image/jpeg" -> ".jpg";
//            case "image/png" -> ".png";
//            case "image/webp" -> ".webp";
//            case "image/gif" -> ".gif";
//
//            case "application/pdf" -> ".pdf";
//
//            case "application/msword" -> ".doc";
//            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
//                    -> ".docx";
//
//            case "application/vnd.ms-excel" -> ".xls";
//            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
//                    -> ".xlsx";
//
//            case "application/vnd.ms-powerpoint" -> ".ppt";
//            case "application/vnd.openxmlformats-officedocument.presentationml.presentation"
//                    -> ".pptx";
//
//            case "text/plain" -> ".txt";
//            case "text/csv" -> ".csv";
//
//            case "application/vnd.oasis.opendocument.text" -> ".odt";
//            case "application/vnd.oasis.opendocument.spreadsheet" -> ".ods";
//            case "application/vnd.oasis.opendocument.presentation" -> ".odp";
//
//            default -> "";
//        };
//    }
//
//    private void validateFile(MultipartFile file) {
//
//        if (file == null || file.isEmpty()) {
//            throw new AppException(ErrorCode.BAD_REQUEST, "File should not be empty");
//        }
//
//        String contentType;
//
//        try {
//            contentType = tika.detect(file.getInputStream());
//        } catch (IOException e) {
//            throw new AppException(ErrorCode.BAD_REQUEST, "Unable to determine file type", e);
//        }
//
//        if (!isSupportedType(contentType)) {
//            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type: " + contentType);
//        }
//    }
//
//    private boolean isSupportedType(String contentType) {
//
//        return switch (contentType) {
//
//            // Images
//            case "image/jpeg",
//                 "image/png",
//                 "image/webp",
//                 "image/gif" -> true;
//
//            // PDF
//            case "application/pdf" -> true;
//
//            // Microsoft Word
//            case "application/msword",
//                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> true;
//
//            // Microsoft Excel
//            case "application/vnd.ms-excel",
//                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> true;
//
//            // Microsoft PowerPoint
//            case "application/vnd.ms-powerpoint",
//                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> true;
//
//            // Text
//            case "text/plain" -> true;
//
//            // CSV
//            case "text/csv" -> true;
//
//            // OpenDocument
//            case "application/vnd.oasis.opendocument.text",
//                 "application/vnd.oasis.opendocument.spreadsheet",
//                 "application/vnd.oasis.opendocument.presentation" -> true;
//
//            default -> false;
//        };
//    }
//
//    public List<Media> validateAndGetMedia(Set<Long> mediaIds, Long ownerId, Long chatroomId) {
//        if (mediaIds == null || mediaIds.isEmpty()) {
//            return List.of();
//        }
//
//        if (mediaIds.size() > 20) {
//            throw new AppException(ErrorCode.BAD_REQUEST, "A message cannot contain more than 20 attachments");
//        }
//
//        List<Media> mediaList = new ArrayList<>();
//
//        for (Long mediaId : mediaIds) {
//
//            Media media = getMedia(mediaId);
//
//            if (!media.getOwnerId().equals(ownerId)) {
//                throw new AppException(ErrorCode.AUTH_FORBIDDEN, "You do not have access to this media");
//            }
//            if (!media.getChatroomId().equals(chatroomId)) {
//                throw new AppException(ErrorCode.AUTH_FORBIDDEN, "Media does not belong to this chatroom");
//            }
//
//            if (media.getState() != MediaState.READY) {
//                throw new AppException(ErrorCode.BAD_REQUEST, "Media is not ready: " + mediaId);
//            }
//            mediaList.add(media);
//        }
//        return mediaList;
//    }
//
//
//    public List<Media> getReadyMediaForMessage(List<Long> mediaIds, Long ownerId, Long chatroomId) {
//        if (mediaIds == null || mediaIds.isEmpty()) {
//            return List.of();
//        }
//
//        List<Media> mediaList = new ArrayList<>();
//
//        for (Long mediaId : mediaIds) {
//
//            Media media = mediaRepository.findByIdAndOwnerIdAndChatroomId(mediaId, ownerId, chatroomId)
//                    .orElseThrow(() -> new AppException(ErrorCode.AUTH_FORBIDDEN, "Media does not belong to this user or chatroom"));
//
//            if (media.getState() != MediaState.READY) {
//                throw new AppException(ErrorCode.BAD_REQUEST, "Media is not ready: " + mediaId);
//            }
//
//            mediaList.add(media);
//        }
//
//        return mediaList;
//    }
//}