package com.tanmaysinghx.newxr.track;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, Long> {
    List<IssueHistory> findByIssueOrderByCreatedAtDesc(Issue issue);
}
