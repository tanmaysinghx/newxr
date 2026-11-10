package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.user.User;
import java.io.Serializable;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** What the session holds for a signed-in user, however they signed in. */
public record NewxrPrincipal(Long id, String email, String name, String role, String via) implements Serializable {

    public static Authentication authenticationFor(User user, String via) {
        NewxrPrincipal p = new NewxrPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name(), via);
        return UsernamePasswordAuthenticationToken.authenticated(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public String toString() {
        return email;
    }
}
