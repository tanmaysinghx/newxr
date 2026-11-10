package com.tanmaysinghx.newxr.track;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Acceptance criteria line. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ChecklistItem {
    @Column(length = 500)
    private String text;
    private boolean done;
}
