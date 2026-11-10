package com.tanmaysinghx.newxr.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Server-wide settings owned by the console (sign-in, Kafka, organization, license record). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 120)
    private String key;

    @Column(name = "setting_value", length = 4000)
    private String value;

    private Instant updatedAt;

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
