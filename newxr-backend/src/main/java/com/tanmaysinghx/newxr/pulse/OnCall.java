package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is on call. Each layer rotates its members every {@code rotationDays} from {@code rotationStart};
 * the first layer is the primary. Overrides replace the primary while they last. The final schedule is
 * the primary with overrides applied.
 */
@Service
@Transactional(readOnly = true)
public class OnCall {

    /** A stretch of time one person covers. {@code override} marks a shift that comes from an override. */
    public record Shift(Instant start, Instant end, User user, boolean override) {}

    private final OnCallLayerRepository layers;
    private final OnCallOverrideRepository overrides;

    public OnCall(OnCallLayerRepository layers, OnCallOverrideRepository overrides) {
        this.layers = layers;
        this.overrides = overrides;
    }

    public List<OnCallLayer> layers(PulseTeam team) {
        return layers.findByTeamOrderByPositionAsc(team);
    }

    public static User userAt(OnCallLayer l, Instant t) {
        int n = l.getMembers().size();
        if (n == 0) return null;
        long period = Duration.ofDays(Math.max(1, l.getRotationDays())).toSeconds();
        long turn = Math.floorDiv(Duration.between(l.getRotationStart(), t).toSeconds(), period);
        return l.getMembers().get((int) Math.floorMod(turn, (long) n));
    }

    /** Rotation shifts of one layer clipped to [from, to). */
    public static List<Shift> shifts(OnCallLayer l, Instant from, Instant to) {
        List<Shift> out = new ArrayList<>();
        if (l.getMembers().isEmpty()) return out;
        Duration period = Duration.ofDays(Math.max(1, l.getRotationDays()));
        long turn = Math.floorDiv(Duration.between(l.getRotationStart(), from).toSeconds(), period.toSeconds());
        Instant start = l.getRotationStart().plus(period.multipliedBy(turn));
        while (start.isBefore(to)) {
            Instant end = start.plus(period);
            out.add(new Shift(start.isBefore(from) ? from : start, end.isAfter(to) ? to : end, userAt(l, start), false));
            start = end;
        }
        return out;
    }

    public List<OnCallOverride> overrides(PulseTeam team, Instant from, Instant to) {
        return overrides.findByTeamAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(team, from, to);
    }

    /** Primary on call at {@code t}, with overrides applied. */
    public User primaryAt(PulseTeam team, Instant t) {
        for (OnCallOverride o : overrides(team, t, t.plusMillis(1))) {
            if (!o.getStartsAt().isAfter(t) && o.getEndsAt().isAfter(t)) return o.getUser();
        }
        List<OnCallLayer> ls = layers(team);
        return ls.isEmpty() ? null : userAt(ls.getFirst(), t);
    }

    public User secondaryAt(PulseTeam team, Instant t) {
        List<OnCallLayer> ls = layers(team);
        return ls.size() < 2 ? null : userAt(ls.get(1), t);
    }

    /** The final schedule over [from, to): primary rotation with overrides on top, adjacent equal shifts merged. */
    public List<Shift> finalShifts(PulseTeam team, Instant from, Instant to) {
        List<OnCallLayer> ls = layers(team);
        List<OnCallOverride> os = overrides(team, from, to);
        TreeSet<Instant> cuts = new TreeSet<>(List.of(from, to));
        if (!ls.isEmpty()) shifts(ls.getFirst(), from, to).forEach(s -> { cuts.add(s.start()); cuts.add(s.end()); });
        for (OnCallOverride o : os) {
            if (o.getStartsAt().isAfter(from) && o.getStartsAt().isBefore(to)) cuts.add(o.getStartsAt());
            if (o.getEndsAt().isAfter(from) && o.getEndsAt().isBefore(to)) cuts.add(o.getEndsAt());
        }
        List<Shift> out = new ArrayList<>();
        Instant prev = null;
        for (Instant c : cuts) {
            if (prev != null) {
                Instant mid = prev.plus(Duration.between(prev, c).dividedBy(2));
                OnCallOverride o = os.stream().filter(x -> !x.getStartsAt().isAfter(mid) && x.getEndsAt().isAfter(mid)).findFirst().orElse(null);
                User u = o != null ? o.getUser() : ls.isEmpty() ? null : userAt(ls.getFirst(), mid);
                Shift last = out.isEmpty() ? null : out.getLast();
                if (last != null && last.override() == (o != null) && sameUser(last.user(), u)) {
                    out.set(out.size() - 1, new Shift(last.start(), c, u, o != null));
                } else if (u != null) {
                    out.add(new Shift(prev, c, u, o != null));
                }
            }
            prev = c;
        }
        return out;
    }

    /** Current primary shift (consecutive shifts by the same person merged) and who comes next. */
    public record Handover(Shift current, User next) {}

    public Handover handover(PulseTeam team, Instant now) {
        List<Shift> s = finalShifts(team, now, now.plus(Duration.ofDays(60)));
        if (s.isEmpty()) return new Handover(null, null);
        Shift first = s.getFirst();
        Instant end = first.end();
        int i = 1;
        while (i < s.size() && sameUser(s.get(i).user(), first.user())) end = s.get(i++).end();
        User next = i < s.size() ? s.get(i).user() : null;
        return new Handover(new Shift(first.start(), end, first.user(), first.override()), next);
    }

    static boolean sameUser(User a, User b) {
        return a == null ? b == null : b != null && Objects.equals(a.getId(), b.getId());
    }
}
