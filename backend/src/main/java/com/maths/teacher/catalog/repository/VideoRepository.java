package com.maths.teacher.catalog.repository;

import com.maths.teacher.catalog.domain.Video;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Query("select distinct v.section from Video v order by v.section asc")
    List<String> findAllSections();

    List<Video> findBySectionOrderByDisplayOrderAsc(String section);
}
