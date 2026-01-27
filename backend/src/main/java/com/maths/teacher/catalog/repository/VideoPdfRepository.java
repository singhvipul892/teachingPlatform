package com.maths.teacher.catalog.repository;

import com.maths.teacher.catalog.domain.VideoPdf;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoPdfRepository extends JpaRepository<VideoPdf, Long> {
    List<VideoPdf> findByVideo_IdInOrderByDisplayOrderAsc(List<Long> videoIds);
    java.util.Optional<VideoPdf> findByIdAndVideo_Id(Long id, Long videoId);
    java.util.Optional<VideoPdf> findByVideo_IdAndPdfType(Long videoId, String pdfType);
}
