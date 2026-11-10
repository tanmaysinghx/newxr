package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByArchivedFalseOrderByIdAsc();
    List<Goal> findByParentOrderByIdAsc(Goal parent);
    List<Goal> findByOwnerAndArchivedFalse(User owner);

    @Query("select g from Goal g where g.archived = false and lower(g.name) like lower(concat('%', :q, '%'))")
    List<Goal> search(String q);
}
