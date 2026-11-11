package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface RepoFileRepository extends JpaRepository<RepoFile, Long> {
    List<RepoFile> findByRepoAndBranch(CodeRepo repo, String branch);
    Optional<RepoFile> findByRepoAndBranchAndPath(CodeRepo repo, String branch, String path);
    void deleteByRepoAndBranch(CodeRepo repo, String branch);
}
