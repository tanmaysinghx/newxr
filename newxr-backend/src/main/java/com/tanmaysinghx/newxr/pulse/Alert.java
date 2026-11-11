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

/** Something a monitor (or a person) raised. Repeats with the same dedup key bump {@link #count}. */
@Getter
@Setter
@Entity
@Table(name = "pulse_alerts")
public class Alert extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    @Column(nullable = false, length = 255) private String title;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String description;
    /** P1..P5 */
    @Column(nullable = false, length = 2) private String priority = "P3";
    /** OPEN, ACKNOWLEDGED, CLOSED */
    @Column(nullable = false, length = 16) private String status = "OPEN";
    @Column(length = 60) private String source;
    @Column(length = 200) private String dedupKey;
    @ManyToOne(fetch = FetchType.LAZY) private User owner;
    @ManyToOne(fetch = FetchType.LAZY) private Integration integration;
    @ManyToOne(fetch = FetchType.LAZY) private Incident incident;
    private int count = 1;
    private Instant lastOccurredAt;
    private Instant acknowledgedAt;
    @ManyToOne(fetch = FetchType.LAZY) private User acknowledgedBy;
    private Instant closedAt;
    private Instant snoozedUntil;
    /** Index of the next escalation step to run; -1 when escalation is finished or stopped. */
    private int escalationStep;
    private Instant nextEscalationAt;
}
