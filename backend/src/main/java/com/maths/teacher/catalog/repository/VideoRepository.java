package com.maths.teacher.catalog.repository;

import com.maths.teacher.catalog.domain.Video;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByChapterIdOrderByDisplayOrderAscIdAsc(Long chapterId);

    /** Course order as students see it: chapter position, then position in the chapter. */
    @Query("""
            select v from Video v, Chapter c
            where v.chapterId = c.id and v.courseId = :courseId
            order by c.displayOrder, c.id, v.displayOrder, v.id
            """)
    List<Video> findByCourseIdInChapterOrder(@Param("courseId") Long courseId);

    @Query("select coalesce(max(v.displayOrder), 0) from Video v where v.chapterId = :chapterId")
    int findMaxDisplayOrderInChapter(@Param("chapterId") Long chapterId);
}
