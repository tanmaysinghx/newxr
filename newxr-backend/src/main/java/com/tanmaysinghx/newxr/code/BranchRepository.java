package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByRepoOrderByUpdatedAtDesc(CodeRepo repo);
    Optional<Branch> findByRepoAndName(CodeRepo repo, String name);
    List<Branch> findByNameContainingIgnoreCase(String part);
}
