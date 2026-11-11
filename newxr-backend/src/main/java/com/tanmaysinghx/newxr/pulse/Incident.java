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


@Getter
@Setter
@Entity
@Table(name = "pulse_incidents")
public class Incident extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    @Column(nullable = false, unique = true, length = 24) private String incidentKey;
    @Column(nullable = false, length = 255) private String title;
    /** INVESTIGATING, IDENTIFIED, MONITORING, RESOLVED */
    @Column(nullable = false, length = 16) private String status = "INVESTIGATING";
    /** P1..P4 */
    @Column(nullable = false, length = 2) private String severity = "P2";
    /** DEGRADED, PARTIAL_OUTAGE, MAJOR_OUTAGE (applied to the affected components while open). */
    @Column(nullable = false, length = 16) private String impact = "DEGRADED";
    @ManyToOne(fetch = FetchType.LAZY) private User commander;
    /** Shown on the public status page. */
    private boolean publicIncident;
    @ManyToMany @JoinTable(name = "pulse_incident_components") private Set<StatusComponent> components = new LinkedHashSet<>();
    @Column(nullable = false) private Instant startedAt;
    private Instant resolvedAt;
}
