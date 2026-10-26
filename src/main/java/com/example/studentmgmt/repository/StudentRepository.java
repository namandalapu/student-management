package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
            select s from Student s
            where lower(s.firstName) like lower(concat('%', :term, '%'))
               or lower(s.lastName) like lower(concat('%', :term, '%'))
               or lower(s.email) like lower(concat('%', :term, '%'))
            order by s.lastName, s.firstName
            """)
    List<Student> search(@Param("term") String term);

    List<Student> findAllByOrderByLastNameAscFirstNameAsc();
}
