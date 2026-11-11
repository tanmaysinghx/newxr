package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByTeamOrderByCreatedAtDesc(PulseTeam team);
    List<Alert> findByTeamAndCreatedAtAfter(PulseTeam team, Instant after);
    Optional<Alert> findFirstByTeamAndDedupKeyAndStatusNotOrderByCreatedAtDesc(PulseTeam team, String dedupKey, String status);
    List<Alert> findByOwnerAndStatusNotOrderByUpdatedAtDesc(User owner, String status);
    List<Alert> findByIncidentOrderByCreatedAtAsc(Incident incident);
    @Query("select a from Alert a where a.status = 'OPEN' and a.nextEscalationAt is not null and a.nextEscalationAt <= :now")
    List<Alert> dueForEscalation(Instant now);
    @Query("select a from Alert a where lower(a.title) like lower(concat('%', :q, '%')) order by a.updatedAt desc")
    List<Alert> search(String q, Pageable page);
}
