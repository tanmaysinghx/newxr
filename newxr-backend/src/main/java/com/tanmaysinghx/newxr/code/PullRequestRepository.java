package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    List<PullRequest> findByRepoOrderByNumberDesc(CodeRepo repo);
    Optional<PullRequest> findByRepoAndNumber(CodeRepo repo, int number);
    List<PullRequest> findByRepoAndSourceBranchAndStatus(CodeRepo repo, String source, String status);
    List<PullRequest> findByAuthorAndStatus(User author, String status);
    long countByRepoAndStatus(CodeRepo repo, String status);
    @Query("select p from PullRequest p where upper(p.title) like concat('%', :key, '%') or upper(p.sourceBranch) like concat('%', :key, '%') order by p.updatedAt desc")
    List<PullRequest> mentioning(String key);
    @Query("select p from PullRequest p where lower(p.title) like lower(concat('%', :q, '%')) order by p.updatedAt desc")
    List<PullRequest> search(String q, Pageable page);
}
