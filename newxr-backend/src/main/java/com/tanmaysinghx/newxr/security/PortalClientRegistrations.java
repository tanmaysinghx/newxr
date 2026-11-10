package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.settings.SettingsService.SettingsChanged;
import com.tanmaysinghx.newxr.settings.SettingsService.SignInSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * The one OAuth client Newxr has: Portal SSO, registration id {@code portal}. Built from the
 * console's sign-in settings (or newxr.properties) and the issuer's discovery document, and
 * rebuilt whenever those settings change, so connecting Portal SSO never needs a restart.
 *
 * <p>Portal SSO enforces PKCE on every client; {@link SecurityConfig} adds it to confidential
 * clients too (Spring adds it to public ones by itself).
 */
@Component
public class PortalClientRegistrations implements ClientRegistrationRepository {

    public static final String REGISTRATION_ID = "portal";
    private static final Logger log = LoggerFactory.getLogger(PortalClientRegistrations.class);

    private final SettingsService settings;
    private volatile ClientRegistration cached;

    public PortalClientRegistrations(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (!REGISTRATION_ID.equals(registrationId)) {
            return null;
        }
        ClientRegistration c = cached;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (cached == null) {
                cached = build();
            }
            return cached;
        }
    }

    @EventListener
    public void onSettingsChanged(SettingsChanged e) {
        if ("sso".equals(e.section())) {
            cached = null;
        }
    }

    private ClientRegistration build() {
        SignInSettings s = settings.signIn();
        if (!s.portalConfigured()) {
            return null;
        }
        try {
            String secret = settings.portalClientSecret();
            boolean confidential = secret != null && !secret.isBlank();
            ClientRegistration.Builder b = ClientRegistrations.fromIssuerLocation(s.portalIssuerUrl())
                    .registrationId(REGISTRATION_ID)
                    .clientName("Portal SSO")
                    .clientId(s.portalClientId())
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .userNameAttributeName("sub")
                    .clientAuthenticationMethod(confidential
                            ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                            : ClientAuthenticationMethod.NONE);
            if (confidential) {
                b.clientSecret(secret);
            }
            return b.build();
        } catch (RuntimeException e) {
            log.warn("Portal SSO at {} is unreachable or not an OIDC issuer: {}", s.portalIssuerUrl(), e.getMessage());
            return null;
        }
    }
}
