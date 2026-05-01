package com.green.imagecore.repositories;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DicomImageRepository extends JpaRepository<DicomImage, Long> {

    List<DicomImage> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<DicomImage> findByIdAndUserId(Long id, Long userId);

    List<DicomImage> findByUserIdAndImportStatusOrderByUploadedAtDesc(Long userId, ImportStatus status);

    List<DicomImage> findByUserIdAndStudyInstanceUidAndImportStatus(Long userId, String studyInstanceUid, ImportStatus status);

    Optional<DicomImage> findBySopInstanceUidAndUserId(String sopInstanceUid, Long userId);

    /**
     * Finds a DicomImage owned by userId where the given sopUid is either the primary
     * sop_instance_uid (single-SOP records) OR is listed inside the sop_instance_uids JSON array
     * (batch records where multiple SOPs share one DB row).
     */
    @Query(value = """
            SELECT * FROM dicom_images
            WHERE user_id = :userId
              AND (sop_instance_uid = :sopUid
                   OR (sop_instance_uids IS NOT NULL
                       AND sop_instance_uids::jsonb @> jsonb_build_array(CAST(:sopUid AS text))))
            LIMIT 1
            """, nativeQuery = true)
    Optional<DicomImage> findBySopUidForUser(@Param("sopUid") String sopUid, @Param("userId") Long userId);

    List<DicomImage> findByUserId(Long userId);

    @Query(value = "SELECT * FROM dicom_images WHERE image_set_id = :imageSetId AND user_id = :userId LIMIT 1",
           nativeQuery = true)
    Optional<DicomImage> findByImageSetIdAndUserId(@Param("imageSetId") String imageSetId,
                                                   @Param("userId") Long userId);

    boolean existsByHealthImagingJobId(String healthImagingJobId);

//    List<List<DicomImage>> findAllBySeriesInstanceUidAndUserId(String seriesInstanceUid, Long userId, Limit limit);
}
