package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound alert webhooks. Each integration has a secret token in its URL
 * ({@code POST /api/public/pulse/hooks/{token}}); only its SHA-256 hash is stored, so the URL is shown once.
 * Accepts a generic JSON body or a Prometheus Alertmanager webhook (detected by its {@code alerts} array).
 */
@RestController
@Transactional
public class IntegrationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final String HOOK_PATH = "/api/public/pulse/hooks/";

    public record IntegrationView(Long id, String name, String format, String icon, String defaultPriority, String tokenHint,
                                  long eventCount, Instant lastEventAt, boolean enabled, Instant createdAt) {}

    /** Returned once, on create and on rotate: the full webhook path including the token. */
    public record Created(IntegrationView integration, String webhookPath) {}

    public record IntegrationRequest(String name, String format, String icon, String defaultPriority, Boolean enabled) {}

    public record HookResult(int raised, int repeated, int resolved) {}

    private final PulseService pulse;
    private final IntegrationRepository integrations;
    private final AlertRepository alerts;
    private final AlertEngine engine;
    private final CurrentUser current;

    public IntegrationService(PulseService pulse, IntegrationRepository integrations, AlertRepository alerts, AlertEngine engine, CurrentUser current) {
        this.pulse = pulse;
        this.integrations = integrations;
        this.alerts = alerts;
        this.engine = engine;
        this.current = current;
    }

    @GetMapping("/api/pulse/teams/{key}/integrations")
    @Transactional(readOnly = true)
    public List<IntegrationView> list(@PathVariable String key) {
        return integrations.findByTeamOrderByIdAsc(pulse.team0(key)).stream().map(IntegrationService::view).toList();
    }

    @PostMapping("/api/pulse/teams/{key}/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@PathVariable String key, @RequestBody IntegrationRequest r) {
        current.require();
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Name the integration.");
        Integration i = new Integration();
        i.setTeam(pulse.team0(key));
        i.setName(r.name().trim());
        apply(i, r);
        String token = newToken(i);
        i = integrations.save(i);
        return new Created(view(i), HOOK_PATH + token);
    }

    @PutMapping("/api/pulse/integrations/{id}")
    public IntegrationView edit(@PathVariable Long id, @RequestBody IntegrationRequest r) {
        current.require();
        Integration i = integration(id);
        if (r.name() != null && !r.name().isBlank()) i.setName(r.name().trim());
        apply(i, r);
        return view(i);
    }

    @PostMapping("/api/pulse/integrations/{id}/rotate")
    public Created rotate(@PathVariable Long id) {
        current.require();
        Integration i = integration(id);
        String token = newToken(i);
        return new Created(view(i), HOOK_PATH + token);
    }

    @DeleteMapping("/api/pulse/integrations/{id}")
    public void delete(@PathVariable Long id) {
        current.require();
        Integration i = integration(id);
        alerts.findByTeamOrderByCreatedAtDesc(i.getTeam()).stream().filter(a -> a.getIntegration() != null && a.getIntegration().getId().equals(id))
                .forEach(a -> a.setIntegration(null));
        integrations.delete(i);
    }

    // ---- inbound -----------------------------------------------------------------

    @PostMapping(HOOK_PATH + "{token}")
    public HookResult receive(@PathVariable String token, @RequestBody Map<String, Object> body) {
        Integration i = integrations.findByTokenHash(hash(token)).filter(Integration::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Integration"));
        i.setEventCount(i.getEventCount() + 1);
        i.setLastEventAt(Instant.now());
        int[] n = new int[3];
        if (body.get("alerts") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<?, ?> labels = map(m.get("labels"));
                Map<?, ?> ann = map(m.get("annotations"));
                String title = first(str(ann.get("summary")), str(labels.get("alertname")), "Alert from " + i.getName());
                String dedup = first(str(m.get("fingerprint")), str(labels.get("alertname")) + labels.hashCode());
                handle(i, str(m.get("status")), title, str(ann.get("description")), priority(str(labels.get("severity"))), dedup, n);
            }
        } else {
            String title = first(str(body.get("title")), str(body.get("summary")), str(body.get("message")));
            if (title == null) throw ApiException.badRequest("Send a title (or an Alertmanager payload).");
            String p = str(body.get("priority"));
            handle(i, first(str(body.get("status")), str(body.get("action")), "firing"), title, str(body.get("description")),
                    p != null && AlertEngine.PRIORITIES.contains(p.toUpperCase(Locale.ROOT)) ? p.toUpperCase(Locale.ROOT) : priority(p),
                    first(str(body.get("dedupKey")), str(body.get("dedup_key")), str(body.get("alias"))), n);
        }
        return new HookResult(n[0], n[1], n[2]);
    }

    private void handle(Integration i, String status, String title, String description, String priority, String dedup, int[] n) {
        String s = status == null ? "firing" : status.toLowerCase(Locale.ROOT);
        if (List.of("resolved", "resolve", "ok", "close", "closed").contains(s)) {
            if (dedup == null) return;
            alerts.findFirstByTeamAndDedupKeyAndStatusNotOrderByCreatedAtDesc(i.getTeam(), dedup, "CLOSED").ifPresent(a -> {
                engine.close(a, null, "resolved by " + i.getName());
                n[2]++;
            });
            return;
        }
        int before = dedup == null ? -1 : alerts.findFirstByTeamAndDedupKeyAndStatusNotOrderByCreatedAtDesc(i.getTeam(), dedup, "CLOSED")
                .map(Alert::getCount).orElse(-1);
        engine.raise(i.getTeam(), new AlertEngine.Raise(trim(title, 255), description, priority, i.getName(), trim(dedup, 200), null), i, null);
        if (before >= 0) n[1]++; else n[0]++;
    }

    // ---- helpers -----------------------------------------------------------------

    private static void apply(Integration i, IntegrationRequest r) {
        if (r.format() != null) {
            if (!List.of("GENERIC", "PROMETHEUS").contains(r.format())) throw ApiException.badRequest("Format must be GENERIC or PROMETHEUS.");
            i.setFormat(r.format());
        }
        if (r.icon() != null) i.setIcon(r.icon());
        if (r.defaultPriority() != null) {
            if (!AlertEngine.PRIORITIES.contains(r.defaultPriority())) throw ApiException.badRequest("Priority must be P1 to P5.");
            i.setDefaultPriority(r.defaultPriority());
        }
        if (r.enabled() != null) i.setEnabled(r.enabled());
    }

    /** Generates a new token for the integration, stores its hash and returns the token itself. */
    static String newToken(Integration i) {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        i.setTokenHash(hash(token));
        i.setTokenHint(token.substring(token.length() - 4));
        return token;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** critical → P1, high/error → P2, warning → P3, info → P4, low → P5; anything else uses the integration default (null). */
    static String priority(String severity) {
        if (severity == null) return null;
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical", "page", "p1" -> "P1";
            case "high", "error", "major", "p2" -> "P2";
            case "warning", "warn", "medium", "p3" -> "P3";
            case "info", "minor", "p4" -> "P4";
            case "low", "p5" -> "P5";
            default -> null;
        };
    }

    private Integration integration(Long id) {
        return integrations.findById(id).orElseThrow(() -> ApiException.notFound("Integration"));
    }

    static IntegrationView view(Integration i) {
        return new IntegrationView(i.getId(), i.getName(), i.getFormat(), i.getIcon(), i.getDefaultPriority(), i.getTokenHint(), i.getEventCount(),
                i.getLastEventAt(), i.isEnabled(), i.getCreatedAt());
    }

    private static Map<?, ?> map(Object o) {
        return o instanceof Map<?, ?> m ? m : new LinkedHashMap<>();
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    private static String first(String... xs) {
        for (String x : xs) if (x != null) return x;
        return null;
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
