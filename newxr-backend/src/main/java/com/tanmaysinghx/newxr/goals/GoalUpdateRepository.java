package com.tanmaysinghx.newxr.goals;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalUpdateRepository extends JpaRepository<GoalUpdate, Long> {
    List<GoalUpdate> findByGoalOrderByCreatedAtDesc(Goal goal);
    List<GoalUpdate> findAllByOrderByCreatedAtDesc(Pageable page);
}
