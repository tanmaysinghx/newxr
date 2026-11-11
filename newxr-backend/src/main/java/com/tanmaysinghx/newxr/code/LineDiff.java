package com.tanmaysinghx.newxr.code;

import java.util.ArrayList;
import java.util.List;

/** Line diff (longest common subsequence) rendered as unified-diff hunks with 3 lines of context. */
public final class LineDiff {

    /** One diff line. {@code type} is ' ' (context), '+' (added) or '-' (removed). */
    public record Line(char type, Integer oldNo, Integer newNo, String text) {}

    public record Hunk(int oldStart, int newStart, List<Line> lines) {}

    /** {@code tooLarge} is set when the files are too big to diff line by line; counts are then approximate. */
    public record Result(int additions, int deletions, List<Hunk> hunks, boolean tooLarge) {}

    static final int CONTEXT = 3;
    private static final long MAX_CELLS = 4_000_000L;

    private LineDiff() {}

    public static Result diff(String before, String after) {
        String[] a = lines(before), b = lines(after);
        if ((long) a.length * b.length > MAX_CELLS) {
            return new Result(b.length, a.length, List.of(), true);
        }
        // Trim common prefix and suffix so the LCS table stays small for typical edits.
        int pre = 0;
        while (pre < a.length && pre < b.length && a[pre].equals(b[pre])) pre++;
        int suf = 0;
        while (suf < a.length - pre && suf < b.length - pre && a[a.length - 1 - suf].equals(b[b.length - 1 - suf])) suf++;
        int n = a.length - pre - suf, m = b.length - pre - suf;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[pre + i].equals(b[pre + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Line> all = new ArrayList<>();
        for (int k = 0; k < pre; k++) all.add(new Line(' ', k + 1, k + 1, a[k]));
        int i = 0, j = 0, adds = 0, dels = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[pre + i].equals(b[pre + j])) {
                all.add(new Line(' ', pre + i + 1, pre + j + 1, a[pre + i]));
                i++;
                j++;
            } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                // Removals before additions, as in unified diffs.
                all.add(new Line('-', pre + i + 1, null, a[pre + i]));
                i++;
                dels++;
            } else {
                all.add(new Line('+', null, pre + j + 1, b[pre + j]));
                j++;
                adds++;
            }
        }
        for (int k = 0; k < suf; k++) all.add(new Line(' ', a.length - suf + k + 1, b.length - suf + k + 1, a[a.length - suf + k]));
        return new Result(adds, dels, hunks(all), false);
    }

    private static List<Hunk> hunks(List<Line> all) {
        List<Hunk> out = new ArrayList<>();
        int k = 0;
        while (k < all.size()) {
            while (k < all.size() && all.get(k).type() == ' ') k++;
            if (k >= all.size()) break;
            int start = Math.max(0, k - CONTEXT);
            int end = k;
            // Extend while changes are within 2*CONTEXT lines of each other.
            int last = k;
            while (end < all.size()) {
                if (all.get(end).type() != ' ') last = end;
                else if (end - last > 2 * CONTEXT) break;
                end++;
            }
            end = Math.min(all.size(), last + CONTEXT + 1);
            List<Line> ls = new ArrayList<>(all.subList(start, end));
            Line first = ls.getFirst();
            int oldStart = first.oldNo() != null ? first.oldNo() : ls.stream().filter(l -> l.oldNo() != null).map(Line::oldNo).findFirst().orElse(0);
            int newStart = first.newNo() != null ? first.newNo() : ls.stream().filter(l -> l.newNo() != null).map(Line::newNo).findFirst().orElse(0);
            out.add(new Hunk(oldStart, newStart, ls));
            k = end;
        }
        return out;
    }

    static String[] lines(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        String t = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        return t.split("\n", -1);
    }
}
