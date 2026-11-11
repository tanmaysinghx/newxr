package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Step of a team escalation policy, run when an alert is still unacknowledged after {@link #delayMinutes}. */
@Getter
@Setter
@Entity
@Table(name = "pulse_escalation_steps")
public class EscalationStep extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    private int position;
    /** ON_CALL_PRIMARY, ON_CALL_SECONDARY, USER, TEAM */
    @Column(nullable = false, length = 20) private String target;
    @ManyToOne(fetch = FetchType.LAZY) private User user;
    /** Minutes after the alert was raised. */
    private int delayMinutes;
}
