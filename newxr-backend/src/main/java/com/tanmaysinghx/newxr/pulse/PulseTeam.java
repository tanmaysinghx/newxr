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

/** An operations team: owns alerts, incidents, an on-call schedule, an escalation policy and a status page. */
@Getter
@Setter
@Entity
@Table(name = "pulse_teams")
public class PulseTeam extends BaseEntity {
    @Column(nullable = false, unique = true, length = 10) private String teamKey;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 300) private String description;
    @Column(length = 9) private String color = "#e5533d";
    @ManyToMany @JoinTable(name = "pulse_team_members") private Set<User> members = new LinkedHashSet<>();
    /** Last incident number; incidents are keyed {teamKey}-{n} (OPS-77). */
    private int incidentSeq;
    /** Public status page at /status/{statusSlug}; null = no page. */
    @Column(unique = true, length = 40) private String statusSlug;
    @Column(length = 80) private String statusTitle;
    private boolean statusPublic;
}
