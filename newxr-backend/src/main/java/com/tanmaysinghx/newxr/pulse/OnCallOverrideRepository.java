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

public interface OnCallOverrideRepository extends JpaRepository<OnCallOverride, Long> {
    List<OnCallOverride> findByTeamAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(PulseTeam team, Instant from, Instant to);
}
