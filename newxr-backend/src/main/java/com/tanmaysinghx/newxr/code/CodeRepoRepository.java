package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface CodeRepoRepository extends JpaRepository<CodeRepo, Long> {
    Optional<CodeRepo> findBySlugIgnoreCase(String slug);
    Optional<CodeRepo> findByCiTokenHash(String hash);
    List<CodeRepo> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CodeRepo r where r.id = :id")
    Optional<CodeRepo> lockById(Long id);
    @Query("select r from CodeRepo r where lower(r.name) like lower(concat('%', :q, '%')) order by r.name")
    List<CodeRepo> search(String q, Pageable page);
}
