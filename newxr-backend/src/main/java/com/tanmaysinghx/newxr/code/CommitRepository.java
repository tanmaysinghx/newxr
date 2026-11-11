package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface CommitRepository extends JpaRepository<Commit, Long> {
    List<Commit> findByRepoAndBranchOrderByCreatedAtDesc(CodeRepo repo, String branch, Pageable page);
    long countByRepoAndBranch(CodeRepo repo, String branch);
    Optional<Commit> findFirstByRepoAndBranchOrderByCreatedAtDesc(CodeRepo repo, String branch);
    @Query("select c from Commit c where upper(cast(c.message as String)) like concat('%', :key, '%') order by c.createdAt desc")
    List<Commit> mentioning(String key, Pageable page);
}
