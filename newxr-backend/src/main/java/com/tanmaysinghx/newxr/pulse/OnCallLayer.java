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

/** One rotation (Primary, Secondary...). Members take turns of {@link #rotationDays} from {@link #rotationStart}. */
@Getter
@Setter
@Entity
@Table(name = "pulse_oncall_layers")
public class OnCallLayer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PulseTeam team;
    @Column(nullable = false, length = 40) private String name;
    private int position;
    @Column(nullable = false) private Instant rotationStart;
    private int rotationDays = 7;
    @ManyToMany @JoinTable(name = "pulse_layer_members") @OrderColumn(name = "turn") private List<User> members = new ArrayList<>();
}
