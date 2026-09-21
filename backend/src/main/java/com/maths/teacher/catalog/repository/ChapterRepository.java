package com.maths.teacher.catalog.repository;

import com.maths.teacher.catalog.domain.Chapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByCourseIdOrderByDisplayOrderAscIdAsc(Long courseId);

    @Query("select coalesce(max(c.displayOrder), 0) from Chapter c where c.courseId = :courseId")
    int findMaxDisplayOrder(@Param("courseId") Long courseId);
}
