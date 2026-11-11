package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.AuthSource;
import com.tanmaysinghx.newxr.user.Group;
import com.tanmaysinghx.newxr.user.GroupRepository;
import com.tanmaysinghx.newxr.user.PasswordPolicy;
import com.tanmaysinghx.newxr.user.Role;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Administration: users (invite, role, app access, deactivate, unlock, reset links), groups, products, security
 * overview and the audit log. Everything under {@code /api/admin} is limited to administrators by SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final SecureRandom RANDOM = new SecureRandom();
    static final Duration INVITE_TTL = Duration.ofDays(7);
    static final Duration RESET_TTL = Duration.ofHours(24);

    public record UserRow(Long id, String name, String email, String initials, String color, String jobTitle, String role, String status,
                          List<String> apps, boolean allApps, String source, boolean hasPassword, Instant lastActiveAt, Instant lockedUntil,
                          Instant createdAt, List<String> groups) {}

    public record Overview(long active, long invited, long guests, long admins, long deactivated, long locked, long newThisMonth,
                           List<ProductView> products, String signIn, String database, boolean kafka, List<AuditView> recent) {}

    public record ProductView(String code, String name, String description, String color, boolean enabled, long users) {}

    public record GroupView(Long id, String name, String description, List<UserRef> members) {}

    public record AuditView(Long id, String actorName, String actorEmail, String action, String target, String detail, String ip, Instant at) {}

    public record SecurityView(String passwordPolicy, int maxFailedLogins, long lockoutMinutes, boolean builtinEnabled, boolean portalEnabled,
                               long ssoOnlyUsers, long admins, List<UserRow> locked, List<UserRow> administrators, String sessionTimeout) {}

    /** {@code link} is the one-time path (/invite/…) to send to the person; it is shown once. */
    public record LinkResult(UserRow user, String link, Instant expiresAt) {}

    public record InviteRequest(String email, String name, String role, List<String> apps, String jobTitle) {}

    public record UserEdit(String name, String jobTitle, String role, List<String> apps, Boolean allApps) {}

    public record GroupRequest(String name, String description, List<Long> memberIds) {}

    public record ProductToggle(Boolean enabled) {}

    private final UserRepository users;
    private final GroupRepository groups;
    private final UserTokenRepository tokens;
    private final ProductService products;
    private final SettingsService settings;
    private final AuditLog audit;
    private final AuditEventRepository auditEvents;
    private final CurrentUser current;
    private final com.tanmaysinghx.newxr.setup.DatabaseConnections databases;
    private final int maxFailed;
    private final Duration lockout;
    private final String sessionTimeout;

    public AdminService(UserRepository users, GroupRepository groups, UserTokenRepository tokens, ProductService products, SettingsService settings,
                        AuditLog audit, AuditEventRepository auditEvents, CurrentUser current,
                        com.tanmaysinghx.newxr.setup.DatabaseConnections databases,
                        @Value("${newxr.security.max-failed-logins:5}") int maxFailed,
                        @Value("${newxr.security.lockout-duration:15m}") Duration lockout,
                        @Value("${server.servlet.session.timeout:12h}") String sessionTimeout) {
        this.users = users;
        this.groups = groups;
        this.tokens = tokens;
        this.products = products;
        this.settings = settings;
        this.audit = audit;
        this.auditEvents = auditEvents;
        this.current = current;
        this.databases = databases;
        this.maxFailed = maxFailed;
        this.lockout = lockout;
        this.sessionTimeout = sessionTimeout;
    }

    // ---- overview -------------------------------------------------------------------

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        List<User> all = users.findAllByOrderByDisplayNameAsc();
        Instant now = Instant.now(), month = now.minus(Duration.ofDays(30));
        var si = settings.signIn();
        String signIn = si.builtinEnabled() && si.portalConfigured() ? "Built-in and Portal SSO" : si.portalConfigured() ? "Portal SSO" : "Built-in";
        var db = databases.status();
        return new Overview(
                all.stream().filter(u -> u.isActive() && !u.isInvited()).count(),
                all.stream().filter(u -> u.isActive() && u.isInvited()).count(),
                all.stream().filter(u -> u.isActive() && u.getRole() == Role.GUEST).count(),
                all.stream().filter(u -> u.isActive() && u.isAdmin()).count(),
                all.stream().filter(u -> !u.isActive()).count(),
                all.stream().filter(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)).count(),
                all.stream().filter(u -> u.getCreatedAt().isAfter(month)).count(),
                products(), signIn, db.product() + " " + db.version() + (db.embedded() ? " (embedded)" : ""), settings.kafka().enabled(),
                auditEvents.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 8)).stream().map(AdminService::view).toList());
    }

    // ---- users ----------------------------------------------------------------------

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserRow> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                               @RequestParam(required = false) String status, @RequestParam(required = false) String app) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Set<String> enabled = products.enabled();
        return users.findAllByOrderByDisplayNameAsc().stream()
                .filter(u -> query.isEmpty() || u.getDisplayName().toLowerCase(Locale.ROOT).contains(query) || u.getEmail().toLowerCase(Locale.ROOT).contains(query))
                .filter(u -> blank(role) || u.getRole().name().equals(role))
                .map(u -> row(u, enabled))
                .filter(r -> blank(status) || r.status().equals(status))
                .filter(r -> blank(app) || r.apps().contains(app))
                .toList();
    }

    /** Creates the account and returns a one-time link where the person sets a password (Portal SSO users can just sign in). */
    @PostMapping("/users/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResult invite(@RequestBody InviteRequest r) {
        User me = current.require();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw ApiException.badRequest("Enter a valid email address.");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw ApiException.conflict(email + " already has an account.");
        User u = new User();
        u.setEmail(email);
        u.setDisplayName(blank(r.name()) ? email.substring(0, email.indexOf('@')) : r.name().trim());
        u.setJobTitle(blank(r.jobTitle()) ? null : r.jobTitle().trim());
        u.setRole(role(r.role()));
        u.setSource(AuthSource.LOCAL);
        u.setInvited(true);
        u.setAppAccess(apps(r.apps(), r.apps() == null));
        u = users.save(u);
        String token = issue(u, "INVITE", INVITE_TTL);
        audit.record(me, "user.invited", email, "Role " + u.getRole().name().toLowerCase(Locale.ROOT));
        return new LinkResult(row(u, products.enabled()), "/invite/" + token, Instant.now().plus(INVITE_TTL));
    }

    @PutMapping("/users/{id}")
    public UserRow edit(@PathVariable Long id, @RequestBody UserEdit r) {
        User me = current.require();
        User u = user(id);
        List<String> changes = new ArrayList<>();
        if (!blank(r.name()) && !r.name().trim().equals(u.getDisplayName())) {
            u.setDisplayName(r.name().trim().substring(0, Math.min(120, r.name().trim().length())));
            changes.add("name");
        }
        if (r.jobTitle() != null) u.setJobTitle(blank(r.jobTitle()) ? null : r.jobTitle().trim());
        if (r.role() != null) {
            Role newRole = role(r.role());
            if (newRole != u.getRole()) {
                if (u.isAdmin() && newRole != Role.ADMIN) requireAnotherAdmin(u, "remove the last administrator's admin role");
                changes.add("role " + u.getRole().name().toLowerCase(Locale.ROOT) + " → " + newRole.name().toLowerCase(Locale.ROOT));
                u.setRole(newRole);
            }
        }
        if (r.apps() != null || Boolean.TRUE.equals(r.allApps())) {
            String v = apps(r.apps(), Boolean.TRUE.equals(r.allApps()));
            if (!java.util.Objects.equals(v, u.getAppAccess())) {
                u.setAppAccess(v);
                changes.add("app access " + (v == null ? "all apps" : v));
            }
        }
        if (!changes.isEmpty()) audit.record(me, "user.updated", u.getEmail(), String.join(", ", changes));
        return row(u, products.enabled());
    }

    @PostMapping("/users/{id}/deactivate")
    public UserRow deactivate(@PathVariable Long id) {
        User me = current.require();
        User u = user(id);
        if (u.getId().equals(me.getId())) throw ApiException.badRequest("You can't deactivate your own account.");
        if (u.isAdmin()) requireAnotherAdmin(u, "deactivate the last administrator");
        u.setActive(false);
        tokens.findByUserAndUsedAtIsNull(u).forEach(t -> t.setUsedAt(Instant.now()));
        audit.record(me, "user.deactivated", u.getEmail(), null);
        return row(u, products.enabled());
    }

    @PostMapping("/users/{id}/reactivate")
    public UserRow reactivate(@PathVariable Long id) {
        User me = current.require();
        User u = user(id);
        u.setActive(true);
        audit.record(me, "user.reactivated", u.getEmail(), null);
        return row(u, products.enabled());
    }

    @PostMapping("/users/{id}/unlock")
    public UserRow unlock(@PathVariable Long id) {
        User me = current.require();
        User u = user(id);
        u.setLockedUntil(null);
        u.setFailedLogins(0);
        audit.record(me, "user.unlocked", u.getEmail(), null);
        return row(u, products.enabled());
    }

    /** One-time link (24 h) for the person to set a new password; the old password keeps working until then. */
    @PostMapping("/users/{id}/reset-link")
    public LinkResult resetLink(@PathVariable Long id) {
        User me = current.require();
        User u = user(id);
        if (!u.isActive()) throw ApiException.badRequest("Reactivate the account first.");
        boolean invite = u.isInvited() && u.getPasswordHash() == null;
        Duration ttl = invite ? INVITE_TTL : RESET_TTL;
        String token = issue(u, invite ? "INVITE" : "RESET", ttl);
        audit.record(me, invite ? "user.invite-resent" : "user.reset-link", u.getEmail(), null);
        return new LinkResult(row(u, products.enabled()), "/invite/" + token, Instant.now().plus(ttl));
    }

    @DeleteMapping("/users/{id}")
    public void delete(@PathVariable Long id) {
        User me = current.require();
        User u = user(id);
        if (!u.isInvited() || u.getLastActiveAt() != null) {
            throw ApiException.badRequest("Only invitations that were never accepted can be deleted. Deactivate the account instead.");
        }
        tokens.deleteAll(tokens.findByUserAndUsedAtIsNull(u));
        groups.findAll().forEach(g -> g.getMembers().removeIf(m -> m.getId().equals(id)));
        try {
            users.delete(u);
            users.flush();
        } catch (RuntimeException e) {
            throw ApiException.conflict("This account is referenced by other records. Deactivate it instead.");
        }
        audit.record(me, "user.invite-revoked", u.getEmail(), null);
    }

    // ---- groups ---------------------------------------------------------------------

    @GetMapping("/groups")
    @Transactional(readOnly = true)
    public List<GroupView> groups() {
        return groups.findAll().stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).map(AdminService::view).toList();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView createGroup(@RequestBody GroupRequest r) {
        User me = current.require();
        if (blank(r.name())) throw ApiException.badRequest("Name the group.");
        if (groups.findAll().stream().anyMatch(g -> g.getName().equalsIgnoreCase(r.name().trim()))) throw ApiException.conflict("That group already exists.");
        Group g = new Group();
        apply(g, r);
        g = groups.save(g);
        audit.record(me, "group.created", g.getName(), g.getMembers().size() + " members");
        return view(g);
    }

    @PutMapping("/groups/{id}")
    public GroupView editGroup(@PathVariable Long id, @RequestBody GroupRequest r) {
        User me = current.require();
        Group g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Group"));
        apply(g, r);
        audit.record(me, "group.updated", g.getName(), g.getMembers().size() + " members");
        return view(g);
    }

    @DeleteMapping("/groups/{id}")
    public void deleteGroup(@PathVariable Long id) {
        User me = current.require();
        Group g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Group"));
        groups.delete(g);
        audit.record(me, "group.deleted", g.getName(), null);
    }

    private void apply(Group g, GroupRequest r) {
        if (!blank(r.name())) g.setName(r.name().trim().substring(0, Math.min(80, r.name().trim().length())));
        if (r.description() != null) g.setDescription(blank(r.description()) ? null : r.description().trim());
        if (r.memberIds() != null) {
            g.getMembers().clear();
            users.findAllById(r.memberIds()).forEach(g.getMembers()::add);
        }
    }

    // ---- products -------------------------------------------------------------------

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductView> products() {
        Set<String> on = products.enabled();
        List<User> active = users.findAllByOrderByDisplayNameAsc().stream().filter(User::isActive).toList();
        return ProductService.ALL.stream().map(p -> new ProductView(p.code(), p.name(), p.description(), p.color(), on.contains(p.code()),
                active.stream().filter(u -> u.isAdmin() || u.apps() == null || u.apps().contains(p.code())).count())).toList();
    }

    @PutMapping("/products/{code}")
    public List<ProductView> toggle(@PathVariable String code, @RequestBody ProductToggle r) {
        User me = current.require();
        if (r.enabled() == null) throw ApiException.badRequest("Say whether the product is enabled.");
        try {
            products.setEnabled(code, r.enabled());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Product");
        }
        audit.record(me, r.enabled() ? "product.enabled" : "product.disabled", code, null);
        return products();
    }

    // ---- security & audit -------------------------------------------------------------

    @GetMapping("/security")
    @Transactional(readOnly = true)
    public SecurityView security() {
        Instant now = Instant.now();
        Set<String> enabled = products.enabled();
        List<User> all = users.findAllByOrderByDisplayNameAsc();
        var si = settings.signIn();
        return new SecurityView(PasswordPolicy.DESCRIPTION, maxFailed, lockout.toMinutes(), si.builtinEnabled(), si.portalConfigured(),
                all.stream().filter(u -> u.isActive() && u.getPasswordHash() == null && u.getExternalSubject() != null).count(),
                all.stream().filter(u -> u.isActive() && u.isAdmin()).count(),
                all.stream().filter(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)).map(u -> row(u, enabled)).toList(),
                all.stream().filter(u -> u.isActive() && u.isAdmin()).map(u -> row(u, enabled)).toList(), sessionTimeout);
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public List<AuditView> audit(@RequestParam(required = false) String q, @RequestParam(required = false) String action,
                                 @RequestParam(defaultValue = "0") int page) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return auditEvents.search(query, action == null ? "" : action.trim(), PageRequest.of(Math.max(0, page), 100)).stream()
                .map(AdminService::view).toList();
    }

    // ---- helpers --------------------------------------------------------------------

    private String issue(User u, String purpose, Duration ttl) {
        Instant now = Instant.now();
        tokens.findByUserAndUsedAtIsNull(u).forEach(t -> t.setUsedAt(now));
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        UserToken t = new UserToken();
        t.setUser(u);
        t.setPurpose(purpose);
        t.setTokenHash(hash(token));
        t.setExpiresAt(now.plus(ttl));
        tokens.save(t);
        return token;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void requireAnotherAdmin(User u, String what) {
        long others = users.findAllByOrderByDisplayNameAsc().stream().filter(x -> x.isActive() && x.isAdmin() && !x.getId().equals(u.getId())).count();
        if (others == 0) throw ApiException.badRequest("You can't " + what + ". Make someone else an administrator first.");
    }

    private static Role role(String r) {
        if (r == null) return Role.MEMBER;
        try {
            return Role.valueOf(r.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Role must be ADMIN, MEMBER or GUEST.");
        }
    }

    /** null = every enabled app. */
    private static String apps(List<String> list, boolean all) {
        if (all || list == null) return null;
        Set<String> known = new LinkedHashSet<>();
        for (String c : list) {
            if (ProductService.ALL.stream().noneMatch(p -> p.code().equals(c))) throw ApiException.badRequest("Unknown app " + c);
            known.add(c);
        }
        return String.join(",", known);
    }

    private UserRow row(User u, Set<String> enabled) {
        Instant now = Instant.now();
        String status = !u.isActive() ? "DEACTIVATED" : u.isInvited() ? "INVITED"
                : u.getLockedUntil() != null && u.getLockedUntil().isAfter(now) ? "LOCKED" : "ACTIVE";
        Set<String> allowed = u.apps();
        List<String> apps = enabled.stream().filter(c -> u.isAdmin() || allowed == null || allowed.contains(c)).toList();
        List<String> gs = groups.findAll().stream().filter(g -> g.getMembers().stream().anyMatch(m -> m.getId().equals(u.getId())))
                .map(Group::getName).sorted().toList();
        return new UserRow(u.getId(), u.getDisplayName(), u.getEmail(), u.initials(), u.color(), u.getJobTitle(), u.getRole().name(), status, apps,
                allowed == null, u.getSource().name(), u.getPasswordHash() != null, u.getLastActiveAt(), u.getLockedUntil(), u.getCreatedAt(), gs);
    }

    private static GroupView view(Group g) {
        return new GroupView(g.getId(), g.getName(), g.getDescription(), g.getMembers().stream().map(UserRef::of)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList());
    }

    private static AuditView view(AuditEvent e) {
        return new AuditView(e.getId(), e.getActorName(), e.getActorEmail(), e.getAction(), e.getTarget(), e.getDetail(), e.getIp(), e.getCreatedAt());
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
