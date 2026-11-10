package com.tanmaysinghx.newxr.goals;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalLinkRepository extends JpaRepository<GoalLink, Long> {
    List<GoalLink> findByGoalOrderByIdAsc(Goal goal);
}
