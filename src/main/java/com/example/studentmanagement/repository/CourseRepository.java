package com.example.studentmanagement.repository;

import com.example.studentmanagement.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<Course> findAllByOrderByCodeAsc();

    /**
     * Loads a course with a row lock (SELECT ... FOR UPDATE) held until the transaction ends.
     * Used before checking or changing a course's capacity, so two concurrent enrollments can't both
     * see the last free seat and overbook the course.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
