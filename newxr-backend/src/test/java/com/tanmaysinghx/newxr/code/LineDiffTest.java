package com.tanmaysinghx.newxr.code;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineDiffTest {

    @Test
    void identicalFilesHaveNoHunks() {
        LineDiff.Result r = LineDiff.diff("a\nb\nc\n", "a\nb\nc\n");
        assertEquals(0, r.additions());
        assertEquals(0, r.deletions());
        assertTrue(r.hunks().isEmpty());
    }

    @Test
    void newFileIsAllAdditions() {
        LineDiff.Result r = LineDiff.diff("", "one\ntwo\n");
        assertEquals(2, r.additions());
        assertEquals(0, r.deletions());
        List<LineDiff.Line> lines = r.hunks().getFirst().lines();
        assertEquals('+', lines.get(0).type());
        assertEquals(1, lines.get(0).newNo());
        assertNull(lines.get(0).oldNo());
    }

    @Test
    void replacedLineIsOneDeletionAndOneAddition() {
        LineDiff.Result r = LineDiff.diff("a\nb\nc\n", "a\nB\nc\n");
        assertEquals(1, r.additions());
        assertEquals(1, r.deletions());
        LineDiff.Hunk h = r.hunks().getFirst();
        assertEquals(1, h.oldStart());
        assertEquals(1, h.newStart());
        assertEquals(List.of(' ', '-', '+', ' '), h.lines().stream().map(LineDiff.Line::type).toList());
    }

    @Test
    void distantChangesGetSeparateHunksWithThreeLinesOfContext() {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            a.append("line ").append(i).append('\n');
            b.append(i == 5 || i == 35 ? "changed " + i : "line " + i).append('\n');
        }
        LineDiff.Result r = LineDiff.diff(a.toString(), b.toString());
        assertEquals(2, r.hunks().size());
        LineDiff.Hunk first = r.hunks().getFirst();
        assertEquals(2, first.oldStart());
        // 3 context + 1 removed + 1 added + 3 context
        assertEquals(8, first.lines().size());
    }

    @Test
    void lineNumbersStayAlignedAfterInsertions() {
        LineDiff.Result r = LineDiff.diff("a\nb\n", "a\nx\ny\nb\n");
        LineDiff.Line last = r.hunks().getFirst().lines().getLast();
        assertEquals(' ', last.type());
        assertEquals(2, last.oldNo());
        assertEquals(4, last.newNo());
    }
}
