package com.tanmaysinghx.newxr.track;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectKeyIgnoreCase(String key);
    boolean existsByProjectKeyIgnoreCase(String key);
    List<Project> findByArchivedFalseOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(Long id);
}
