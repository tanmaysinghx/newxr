package com.tanmaysinghx.newxr.track;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectOrderByIdAsc(Project p);
    List<Sprint> findByProjectAndStateOrderByIdAsc(Project p, SprintState state);
    Optional<Sprint> findFirstByProjectAndState(Project p, SprintState state);
    long countByProject(Project p);
}
