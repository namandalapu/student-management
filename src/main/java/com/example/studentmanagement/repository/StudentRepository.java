package com.example.studentmanagement.repository;

import com.example.studentmanagement.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Case-insensitive "contains" match on first name, last name, or email. The term is matched literally:
     * % and _ typed by the user are escaped so they don't act as LIKE wildcards.
     */
    default List<Student> search(String term) {
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return searchByPattern("%" + escaped.toLowerCase() + "%");
    }

    @Query("""
            select s from Student s
            where lower(s.firstName) like :pattern escape '\\'
               or lower(s.lastName) like :pattern escape '\\'
               or lower(s.email) like :pattern escape '\\'
            order by s.lastName, s.firstName
            """)
    List<Student> searchByPattern(@Param("pattern") String pattern);

    List<Student> findAllByOrderByLastNameAscFirstNameAsc();
}
