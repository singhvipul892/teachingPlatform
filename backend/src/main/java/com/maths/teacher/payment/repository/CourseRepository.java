package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.Course;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByActiveTrue();
}
