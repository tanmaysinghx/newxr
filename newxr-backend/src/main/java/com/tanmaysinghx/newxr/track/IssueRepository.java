package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IssueRepository extends JpaRepository<Issue, Long> {
    Optional<Issue> findByIssueKeyIgnoreCase(String key);
    List<Issue> findByProjectOrderByRankAsc(Project p);
    List<Issue> findBySprintOrderByRankAsc(Sprint s);
    List<Issue> findByProjectAndSprintIsNullAndTypeNotOrderByRankAsc(Project p, IssueType excluded);
    List<Issue> findByParentOrderByIdAsc(Issue parent);
    List<Issue> findByProjectAndTypeOrderByStartDateAsc(Project p, IssueType type);
    List<Issue> findByAssigneeOrderByUpdatedAtDesc(User assignee, Pageable page);
    long countByProject(Project p);

    @Query("select coalesce(max(i.rank), 0) from Issue i where i.project = :p")
    double maxRank(Project p);

    @Query("""
        select i from Issue i where lower(i.summary) like lower(concat('%', :q, '%'))
          or i.issueKey = :key or lower(cast(i.description as String)) like lower(concat('%', :q, '%'))
        order by i.updatedAt desc""")
    List<Issue> search(String q, String key, Pageable page);
}
