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

/** Inbound webhook. Only a SHA-256 hash of the token is stored; the token is shown once. */
@Getter
@Setter
@Entity
@Table(name = "pulse_integrations")
public class Integration extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    @Column(nullable = false, length = 80) private String name;
    /** GENERIC, PROMETHEUS */
    @Column(nullable = false, length = 16) private String format = "GENERIC";
    @Column(length = 24) private String icon = "server";
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(length = 8) private String tokenHint;
    @Column(nullable = false, length = 2) private String defaultPriority = "P3";
    private long eventCount;
    private Instant lastEventAt;
    private boolean enabled = true;
}
