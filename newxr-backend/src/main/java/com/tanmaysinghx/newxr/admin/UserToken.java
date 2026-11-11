package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One-time link to set a password: an invitation or an administrator-issued reset. Only the hash is stored. */
@Getter
@Setter
@Entity
@Table(name = "user_tokens")
public class UserToken extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    /** INVITE or RESET */
    @Column(nullable = false, length = 10) private String purpose;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
}
