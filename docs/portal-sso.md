# Signing in with Portal SSO

Newxr can sign people in through Portal SSO, or any OpenID Connect provider, as a confidential client using the authorization code flow with PKCE. It can run alongside built-in passwords or replace them.

## 1. Register Newxr in Portal SSO

Create a client (application) with:

| Setting | Value |
|---|---|
| Client type | Confidential |
| Grant type | Authorization code, PKCE (S256) |
| Redirect URI | `<NEWXR_PUBLIC_URL>/login/oauth2/code/portal`, shown in **Administration → Sign-in** |
| Scopes | `openid profile email` |

Copy the issuer URL, client ID and client secret.

## 2. Configure Newxr

Either in the setup wizard (step 3), or later in **Administration → Sign-in**:

1. Turn on **Portal SSO** and paste the issuer URL. **Test** fetches its OpenID configuration.
2. Enter the client ID and client secret. The secret is encrypted at rest and never shown again.
3. Choose the button label ("Continue with SSO").
4. **Create a Newxr account the first time someone signs in**: on, anyone your Portal lets through gets a member account; off, people need an invitation first (**Administration → Users → Invite**).

Or with configuration only (before the first save in the console):

```properties
newxr.sso.portal.issuer-url=https://sso.example.com
newxr.sso.portal.client-id=newxr
newxr.sso.portal.client-secret=...
newxr.sso.builtin-enabled=true
```

Once saved in the console, the console's values win.

## How accounts are matched

On sign-in Newxr looks for an account by the provider's subject, then by email. A match is linked to that subject, and its display name is refreshed from the token. Deactivated accounts are refused.

## Turning off passwords

You can turn built-in passwords off so everyone uses SSO. **Administrators keep their password** as a way back in if the provider is down or misconfigured. Invitation links still let a new administrator set one.

## Troubleshooting

| Message on the login page | Cause |
|---|---|
| Portal SSO is not reachable right now | The issuer URL can't be reached from the Newxr server |
| Your Portal SSO account has no Newxr access yet | Automatic accounts are off and the person wasn't invited |
| Portal SSO did not share an email address | Add the `email` scope / claim for this client |
| Redirect URI mismatch (at the provider) | `NEWXR_PUBLIC_URL` differs from the address registered in Portal SSO |
