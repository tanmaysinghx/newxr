package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track_history")
public class IssueHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Issue issue;
    @ManyToOne(fetch = FetchType.LAZY) private User actor;
    @Column(length = 40) private String field;
    @Column(length = 300) private String fromValue;
    @Column(length = 300) private String toValue;
}
