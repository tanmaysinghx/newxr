package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.User;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The seven apps, which of them the organization has switched on, and which a user may open. */
@Service
public class ProductService {

    public record Product(String code, String name, String description, String color) {}

    public static final List<Product> ALL = List.of(
            new Product("track", "Newxr Track", "Plan, track and ship work", "#5a4bf5"),
            new Product("pages", "Newxr Pages", "Docs and knowledge for every team", "#0e9f8e"),
            new Product("boards", "Newxr Boards", "Visual boards for anything", "#e8892a"),
            new Product("code", "Newxr Code", "Git repos, pull requests, pipelines", "#2f6fde"),
            new Product("desk", "Newxr Desk", "Service desk and help center", "#d9466f"),
            new Product("pulse", "Newxr Pulse", "Alerts, on-call and status pages", "#e5533d"),
            new Product("goals", "Newxr Goals", "Goals and project updates", "#2f9a57"));

    private final SettingsService settings;

    public ProductService(SettingsService settings) {
        this.settings = settings;
    }

    public Set<String> disabled() {
        String v = settings.get(SettingsService.PRODUCTS_DISABLED, "");
        return v.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(v.split(",")));
    }

    public Set<String> enabled() {
        Set<String> off = disabled();
        return ALL.stream().map(Product::code).filter(c -> !off.contains(c)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setEnabled(String code, boolean on) {
        if (ALL.stream().noneMatch(p -> p.code().equals(code))) {
            throw new IllegalArgumentException("Unknown product " + code);
        }
        Set<String> off = new LinkedHashSet<>(disabled());
        if (on) off.remove(code); else off.add(code);
        settings.put(java.util.Map.of(SettingsService.PRODUCTS_DISABLED, String.join(",", off)));
    }

    /** Enabled apps the user may open; administrators see every enabled app. */
    public Set<String> appsFor(User u) {
        Set<String> on = enabled();
        Set<String> allowed = u.apps();
        if (allowed == null || u.isAdmin()) {
            return on;
        }
        on.retainAll(allowed);
        return on;
    }
}
