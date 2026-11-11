package com.tanmaysinghx.newxr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PortalClientRegistrations registrations,
            PortalLoginSuccessHandler portalSuccess,
            SecurityContextRepository contextRepository) throws Exception {

        RequestMatcher api = request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/");

        http
            .securityContext(c -> c.securityContextRepository(contextRepository))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                // Everything else is the single-page app and its assets, which handle sign-in
                // themselves; the data behind them is under /api.
                .anyRequest().permitAll())
            // Alert and CI webhooks authenticate with the secret token in their URL and are called by tools, not browsers.
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/public/pulse/hooks/**", "/api/public/code/hooks/**"))
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
            .addFilterBefore(new PortalAvailabilityFilter(registrations),
                    org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class)
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("NEWXR_SESSION"))
            .oauth2Login(o -> o
                .clientRegistrationRepository(registrations)
                .loginPage("/login")
                .authorizationEndpoint(a -> a.authorizationRequestResolver(pkceResolver(registrations)))
                .successHandler(portalSuccess)
                .failureUrl("/login?error=sso_failed"))
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' data: https://fonts.gstatic.com; script-src 'self'; connect-src 'self'; frame-ancestors 'self'; "
                    + "form-action 'self' https: http://localhost:* http://127.0.0.1:*")));
        return http.build();
    }

    /** PKCE on the confidential client too: Portal SSO requires it on every client. */
    private static OAuth2AuthorizationRequestResolver pkceResolver(PortalClientRegistrations registrations) {
        DefaultOAuth2AuthorizationRequestResolver r =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        r.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return r;
    }

    /**
     * "Continue with SSO" while Portal SSO is not configured or not reachable: back to the sign-in
     * page with a reason, rather than an error page.
     */
    static final class PortalAvailabilityFilter extends OncePerRequestFilter {
        private final PortalClientRegistrations registrations;

        PortalAvailabilityFilter(PortalClientRegistrations registrations) {
            this.registrations = registrations;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/oauth2/authorization/")
                    && registrations.findByRegistrationId(PortalClientRegistrations.REGISTRATION_ID) == null) {
                response.sendRedirect(request.getContextPath() + "/login?error=sso_unavailable");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** Makes sure the XSRF-TOKEN cookie exists before the SPA's first POST (tokens are deferred). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
