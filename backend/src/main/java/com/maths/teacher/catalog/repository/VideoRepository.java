package com.maths.teacher.catalog.repository;

import com.maths.teacher.catalog.domain.Video;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByCourseIdOrderByDisplayOrderAsc(Long courseId);
}
