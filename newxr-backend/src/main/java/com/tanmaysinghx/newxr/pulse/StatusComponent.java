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

/** A service shown on the status page. Its status comes from open incidents, or a manual override. */
@Getter
@Setter
@Entity
@Table(name = "pulse_components")
public class StatusComponent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    @Column(nullable = false, length = 80) private String name;
    @Column(length = 200) private String description;
    private int position;
    /** OPERATIONAL, DEGRADED, PARTIAL_OUTAGE, MAJOR_OUTAGE, MAINTENANCE — manual status when no incident applies. */
    @Column(nullable = false, length = 16) private String manualStatus = "OPERATIONAL";
}
