package com.tanmaysinghx.newxr.track;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {
    List<IssueComment> findByIssueOrderByCreatedAtAsc(Issue issue);
    long countByIssue(Issue issue);
}
