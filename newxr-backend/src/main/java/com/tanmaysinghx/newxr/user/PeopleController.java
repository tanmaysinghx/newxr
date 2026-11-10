package com.tanmaysinghx.newxr.user;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Directory of active people (pickers, mentions, People page) and the signed-in user's profile. */
@RestController
public class PeopleController {

    public record Person(Long id, String name, String email, String initials, String color, String jobTitle,
                         String role, Instant lastActiveAt) {
        static Person of(User u) {
            return new Person(u.getId(), u.getDisplayName(), u.getEmail(), u.initials(), u.color(), u.getJobTitle(),
                    u.getRole().name(), u.getLastActiveAt());
        }
    }

    public record ProfileUpdate(String name, String jobTitle) {}

    public record PasswordChange(String currentPassword, String newPassword) {}

    private final UserRepository users;
    private final CurrentUser current;
    private final PasswordEncoder encoder;

    public PeopleController(UserRepository users, CurrentUser current, PasswordEncoder encoder) {
        this.users = users;
        this.current = current;
        this.encoder = encoder;
    }

    @GetMapping("/api/people")
    @Transactional(readOnly = true)
    public List<Person> people(@RequestParam(required = false) String q) {
        List<User> list = q == null || q.isBlank() ? users.findAllByOrderByDisplayNameAsc() : users.search(q.trim());
        return list.stream().filter(User::isActive).limit(200).map(Person::of).toList();
    }

    @GetMapping("/api/profile")
    @Transactional(readOnly = true)
    public Person profile() {
        return Person.of(current.require());
    }

    @PutMapping("/api/profile")
    @Transactional
    public Person update(@RequestBody ProfileUpdate body) {
        User u = current.require();
        if (body.name() != null && !body.name().isBlank()) {
            u.setDisplayName(body.name().trim().substring(0, Math.min(120, body.name().trim().length())));
        }
        u.setJobTitle(body.jobTitle() == null || body.jobTitle().isBlank() ? null : body.jobTitle().trim());
        return Person.of(users.save(u));
    }

    @PostMapping("/api/profile/password")
    @Transactional
    public void changePassword(@RequestBody PasswordChange body) {
        User u = current.require();
        if (u.getPasswordHash() != null && (body.currentPassword() == null || !encoder.matches(body.currentPassword(), u.getPasswordHash()))) {
            throw ApiException.badRequest("Your current password is not correct.");
        }
        PasswordPolicy.check(body.newPassword());
        u.setPasswordHash(encoder.encode(body.newPassword()));
        users.save(u);
    }
}
