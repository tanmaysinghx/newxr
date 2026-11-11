package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {
    List<Pipeline> findByRepoOrderByNumberDesc(CodeRepo repo, Pageable page);
    Optional<Pipeline> findByRepoAndNumber(CodeRepo repo, int number);
    Optional<Pipeline> findByRepoAndExternalId(CodeRepo repo, String externalId);
    Optional<Pipeline> findFirstByRepoAndBranchOrderByNumberDesc(CodeRepo repo, String branch);
    @Query("select p from Pipeline p where upper(p.branch) like concat('%', :key, '%') order by p.number desc")
    List<Pipeline> mentioning(String key, Pageable page);
}
