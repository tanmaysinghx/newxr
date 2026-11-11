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
@Table(name = "code_pull_reviewers")
public class PullReviewer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PullRequest pull;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    /** PENDING, APPROVED, NEEDS_WORK */
    @Column(nullable = false, length = 12) private String state = "PENDING";
}
