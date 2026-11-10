package com.tanmaysinghx.newxr.setup;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Checks that an address is a Portal SSO (OIDC) issuer before it is saved. */
@Service
public class PortalDiscovery {

    public record TestResult(boolean ok, String message, String issuer, String authorizationEndpoint) {}

    private final RestClient http;

    public PortalDiscovery() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(6));
        f.setReadTimeout(Duration.ofSeconds(8));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    @SuppressWarnings("unchecked")
    public TestResult test(String issuerUrl) {
        if (issuerUrl == null || issuerUrl.isBlank()) {
            return new TestResult(false, "Enter the Portal SSO address.", null, null);
        }
        String issuer = issuerUrl.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(issuer + "/.well-known/openid-configuration");
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            return new TestResult(false, "That is not an http(s) address.", null, null);
        }
        try {
            Map<String, Object> d = http.get().uri(uri).retrieve().body(Map.class);
            if (d == null || !issuer.equals(String.valueOf(d.get("issuer")).replaceAll("/+$", ""))) {
                return new TestResult(false, "That address answers, but names a different issuer ("
                        + (d == null ? "none" : d.get("issuer")) + "). Use exactly the issuer URL.", null, null);
            }
            return new TestResult(true, "Found Portal SSO at " + issuer + ".", issuer, String.valueOf(d.get("authorization_endpoint")));
        } catch (Exception e) {
            return new TestResult(false, "No OpenID Connect discovery document at " + uri + ".", null, null);
        }
    }
}
