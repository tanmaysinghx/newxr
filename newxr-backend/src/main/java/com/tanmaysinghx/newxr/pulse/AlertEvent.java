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

/** Alert timeline: raised, repeated, acknowledged, escalated, note... */
@Getter
@Setter
@Entity
@Table(name = "pulse_alert_events")
public class AlertEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Alert alert;
    @ManyToOne(fetch = FetchType.LAZY) private User actor;
    @Column(nullable = false, length = 24) private String kind;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String text;
}
