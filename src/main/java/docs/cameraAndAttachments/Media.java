package docs.cameraAndAttachments;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "media",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_media_chatroom_checksum",
                        columnNames = {"chatroom_id", "checksum_sha256"}
                )
        }
)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "chatroom_id")
    private Long chatroomId;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "kind", nullable = false, length = 50)
//    private MediaKind kind;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "state", nullable = false, length = 20)
//    private MediaState state = MediaState.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
//
//    @PrePersist
//    protected void onCreate() {
//        LocalDateTime now = LocalDateTime.now();
//
//        createdAt = now;
//        updatedAt = now;
//
//        if (state == null) {
//            state = MediaState.PENDING;
//        }
//    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}