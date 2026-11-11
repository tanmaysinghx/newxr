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

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByIncidentKeyIgnoreCase(String key);
    List<Incident> findByTeamOrderByStartedAtDesc(PulseTeam team);
    List<Incident> findByTeamAndStartedAtAfterOrderByStartedAtDesc(PulseTeam team, Instant after);
    List<Incident> findByTeamAndStatusNot(PulseTeam team, String status);
    List<Incident> findByCommanderAndStatusNot(User commander, String status);
    @Query("select i from Incident i where lower(i.title) like lower(concat('%', :q, '%')) or i.incidentKey = :key order by i.startedAt desc")
    List<Incident> search(String q, String key, Pageable page);
}
