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
@Table(name = "code_deployments")
public class Deployment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    @Column(nullable = false, length = 40) private String environment;
    @Column(length = 40) private String commitSha;
    @Column(length = 100) private String branch;
    @Column(length = 60) private String version;
    /** IN_PROGRESS, SUCCEEDED, FAILED */
    @Column(nullable = false, length = 12) private String status = "SUCCEEDED";
    private Integer pipelineNumber;
    @Column(length = 120) private String deployedBy;
    @Column(length = 400) private String url;
}
