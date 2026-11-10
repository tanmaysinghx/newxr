package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "desk_comments")
public class RequestComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
    /** Internal notes are visible to agents only. */
    private boolean internal;
}
