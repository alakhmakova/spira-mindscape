# Google Drive / Docs integration — a reusable recipe

How to let a logged-in user turn app content (here: a note's HTML) into a real
**Google Doc in their own Drive**, with one click. Written so you can repeat it in
any **Spring Boot + SPA** project, not just Spira.

This is the "offline" variant: the backend stores a **refresh token** so it can
create documents on the user's behalf even after the original login token expires
— no re-login each time.

> **Spira's concrete implementation** (use as a reference while reading):
> - `backend/.../config/SecurityConfig.java` — offline access + login success handler
> - `backend/.../auth/OAuth2LoginSuccessHandler.java` — captures & stores the refresh token
> - `backend/.../googledocs/GoogleDriveService.java` — token refresh + Drive upload
> - `backend/.../googledocs/GoogleDocsController.java` — `POST /api/notes/google-doc`
> - `src/components/spira/note-export.ts` — `openInGoogleDocs` / `createGoogleDocFromHtml`
> - migration `V11__app_user_refresh_token.sql`

---

## How it works (the whole flow)

```
1. Login: app asks Google for offline access (access_type=offline, prompt=consent)
              │
              ▼
2. Google returns an access token + a REFRESH token
              │
              ▼
3. Success handler stores the refresh token, ENCRYPTED, on the user row
              │
   ... later, user clicks "Open in Google Docs" ...
              │
              ▼
4. Backend: refresh token ──(token endpoint)──► fresh access token
              │
              ▼
5. Backend: POST the HTML to Drive files.create with mimeType
   application/vnd.google-apps.document  → Drive converts it to a Doc
              │
              ▼
6. Backend returns the doc's webViewLink → frontend opens it in a new tab
```

Two ideas do the heavy lifting:
- **Offline access** → you get a refresh token, so step 4 works any time.
- **`mimeType: application/vnd.google-apps.document` on upload** → Drive converts
  your `text/html` (or `text/plain`, `.docx`, etc.) into a native, editable Google Doc.

---

## Part A — Google Cloud Console (one-time, manual)

1. **Create / pick a project**, then **APIs & Services → Library → enable
   "Google Drive API"**. (The Docs API is *not* required — Drive's conversion does it.)
2. **OAuth consent screen:**
   - User type: External (or Internal for a Workspace org).
   - Add the scope **`https://www.googleapis.com/auth/drive.file`**. This is the
     least-privilege Drive scope — your app can only see/manage files **it created**,
     not the user's whole Drive.
   - `drive.file` is a **sensitive** scope. While the app is in **Testing** status it
     works for accounts you add as **Test users** without Google verification. For a
     public production app, Google requires app verification.
3. **Credentials → OAuth client ID (Web application):**
   - Authorized redirect URI: `https://<your-host>/login/oauth2/code/google`
     (and `http://localhost:8080/login/oauth2/code/google` for local dev).
   - Note the **client id** and **client secret**.

> ⚠️ Google does **not** allow private-IP or plain-HTTP redirect URIs (except
> `localhost`). To test on a phone use an HTTPS tunnel (cloudflared/ngrok) or a
> deployed HTTPS domain.

---

## Part B — Spring backend

### B0. Dependencies

```xml
<dependency><groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId></dependency>
```

### B1. Request offline access (so Google issues a refresh token)

By default Spring's Google login does **not** ask for offline access, so you never
get a refresh token. Add `access_type=offline` and `prompt=consent` to the
authorization request via a custom resolver. `prompt=consent` forces the consent
screen each login, which guarantees a refresh token (Google otherwise only returns
one on the very first consent).

```java
// in your SecurityFilterChain
.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(authz -> authz
        .authorizationRequestResolver(offlineAccessResolver(clientRegistrationRepository)))
    .successHandler(myLoginSuccessHandler)   // see B2
)

private OAuth2AuthorizationRequestResolver offlineAccessResolver(ClientRegistrationRepository repo) {
    var resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(c -> c.additionalParameters(p -> {
        p.put("access_type", "offline");
        p.put("prompt", "consent");
    }));
    return resolver;
}
```

Add the scope in `application.properties`:

```properties
spring.security.oauth2.client.registration.google.scope=openid,email,profile,https://www.googleapis.com/auth/drive.file
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
```

### B2. Capture and store the refresh token (encrypted)

Spring stores the `OAuth2AuthorizedClient` (with the refresh token) in an
`OAuth2AuthorizedClientService` right after login. Read it in a success handler and
persist the refresh token **encrypted** on your user row. Never store it in plaintext.

```java
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    // ctor: inject OAuth2AuthorizedClientService, your UserRepository, an EncryptionService,
    //       and setDefaultTargetUrl(frontendUrl); setAlwaysUseDefaultTargetUrl(true);

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication auth) throws IOException, ServletException {
        if (auth instanceof OAuth2AuthenticationToken token) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    token.getAuthorizedClientRegistrationId(), auth.getName());
            OAuth2RefreshToken refresh = client != null ? client.getRefreshToken() : null;
            if (refresh != null) {                     // null if Google didn't issue one this time
                user.setEncRefreshToken(encryption.encrypt(refresh.getTokenValue()));
                userRepository.save(user);             // keep the old token if refresh == null
            }
        }
        super.onAuthenticationSuccess(req, res, auth); // redirect to the SPA
    }
}
```

Storage: add an `enc_refresh_token` column (TEXT) to your user table (a migration),
and encrypt with AES-256-GCM (Spira reuses its `EncryptionService`). Wrap the whole
capture in try/catch so a capture failure never blocks login.

### B3. Exchange refresh token → access token, then create the Doc

When the user asks to export, mint a fresh access token from the stored refresh
token (Google token endpoint), then do a Drive **multipart** upload.

```java
// 1) refresh -> access token  (application/x-www-form-urlencoded POST to token endpoint)
String form = "grant_type=refresh_token"
    + "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret)
    + "&refresh_token=" + enc(refreshToken);
// POST google.getProviderDetails().getTokenUri()  → parse "access_token"

// 2) create the Doc: multipart/related, metadata part + HTML media part
String boundary = "x-" + UUID.randomUUID();
String body =
    "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
  + "{\"name\":\"My Doc\",\"mimeType\":\"application/vnd.google-apps.document\"}\r\n"
  + "--" + boundary + "\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n"
  + html + "\r\n--" + boundary + "--";

// POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink
//   Authorization: Bearer <access token>
//   Content-Type: multipart/related; boundary=<boundary>
// → parse "webViewLink" from the JSON response
```

You don't need the Google client library — a plain `java.net.http.HttpClient` keeps
dependencies small. Get `clientId` / `clientSecret` / `tokenUri` from
`ClientRegistrationRepository.findByRegistrationId("google")` rather than hard-coding.

Handle failures explicitly: a revoked/expired refresh token → return a clear
"sign in again" error; a non-2xx from Drive → surface the status + body.

### B4. The endpoint

```java
@PostMapping("/api/notes/google-doc")
public CreateDocResponse create(@RequestBody @Valid CreateDocRequest req) {
    AppUser user = currentUserProvider.getCurrentUser();
    return new CreateDocResponse(driveService.createGoogleDoc(user, req.title(), req.html()));
}
```

It's a normal authenticated endpoint, so your existing auth + CSRF rules apply.

---

## Part C — Frontend (SPA)

Two things matter:

1. **Send the session cookie + CSRF token** (it's a mutating, authenticated call):
   ```ts
   const res = await fetch("/api/notes/google-doc", {
     method: "POST",
     credentials: "include",
     headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": getCsrfToken() },
     body: JSON.stringify({ title, html }),
   });
   const { webViewLink } = await res.json();
   ```
2. **Beat the popup blocker.** You can't `window.open` after an `await` from a click
   handler — browsers block it. Reserve the tab synchronously, then set its URL once
   the request resolves:
   ```ts
   const tab = window.open("", "_blank");        // during the click (allowed)
   try {
     const link = await createGoogleDoc(title, html);  // async
     if (tab) tab.location.href = link; else window.open(link, "_blank");
   } catch (e) { tab?.close(); throw e; }
   ```

Tip: keep the network call (no DOM) and the popup/DOM bits in separate functions —
the network function is then unit-testable in a Node test runner without a browser.

---

## Part D — Gotchas (read before you ship)

| Issue | What to do |
|---|---|
| No refresh token arrives | You forgot `access_type=offline`, or Google already consented before — add `prompt=consent` to force it. |
| `redirect_uri_mismatch` | Register the exact redirect URI; no private IPs / plain HTTP (except localhost). |
| Sensitive-scope warning | `drive.file` needs verification for public apps; in Testing status add yourself as a test user. |
| Refresh token revoked / expired | Catch the token-endpoint 4xx and tell the user to sign in again. |
| Popup blocked | Reserve the tab synchronously (Part C). |
| Behind a proxy (Cloud Run, etc.) | Set `server.forward-headers-strategy=framework` so OAuth builds `https://` redirect URIs. |
| Security | Encrypt the refresh token at rest; never log it; least-privilege scope (`drive.file`, not full `drive`). |
| Consent friction | `prompt=consent` shows the consent screen every login. If that's too much, drop it and accept that the refresh token only comes on first consent (store it then). |

---

## Part E — Testing

- **Backend unit:** guard path (no refresh token → clear error) and the multipart
  body builder (metadata part before HTML part). The live Google call isn't unit-tested.
- **Backend integration:** the endpoint's security — anonymous → 401, authenticated
  without CSRF → 403, authenticated + CSRF → 200 with the Drive service **mocked**.
- **Frontend:** the network function sends `credentials` + the CSRF header and returns
  the link (mock `fetch`).

See the Spira test files: `GoogleDriveServiceTest`, `GoogleDocsSecurityIntegrationTest`,
`note-export.test.ts`.
