package com.green.imagecore.repositories;

import com.green.imagecore.entities.DicomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DicomImageRepository extends JpaRepository<DicomImage, Long> {

    List<DicomImage> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<DicomImage> findByIdAndUserId(Long id, Long userId);
}
