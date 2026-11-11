package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Getter
@Setter
@Entity
@Table(name = "code_pipeline_stages")
public class PipelineStage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Pipeline pipeline;
    private int position;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 10) private String status = "QUEUED";
    private Integer durationSeconds;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String log;
}
