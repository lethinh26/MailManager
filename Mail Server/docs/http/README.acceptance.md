# Disposable HTTPS acceptance (Newman)

`acceptance.postman_collection.json` is a Postman v2.1 collection of 140 ordered real HTTP requests and assertions for backend Auth, Admin, Settings, Audit, OAuth and mailbox ownership. Run it as a single iteration against the **disposable acceptance server only**, after the Java E2E suite has finished, or against a separately initialized disposable server. Do not import or run it against an existing Postman workspace, development database, real Gmail account or deployed service.

## Harness contract

The fixture must have its own fresh database, random secrets, an HTTPS listener bound to loopback on a non-default port, and an independently generated certificate for `localhost`/`127.0.0.1`. The harness must verify that its child process owns the listener. IMAP and SMTP must be isolated loopback fixtures (or unreachable loopback endpoints), and the Google token/UserInfo/profile/revoke URLs must point to the disposable local OAuth provider. Use a dedicated disposable ADMIN with a random password and `mustChangePassword=false`; never give the collection a production or existing developer account. It does not change the supplied admin's password, role or enabled status.

The OAuth provider must accept the synthetic authorization code `fixture-<code_challenge>` only once, verify that SHA-256 of the submitted PKCE verifier matches that challenge, return unique fixture access/refresh tokens with the Gmail scope, and return the verified UserInfo email and Gmail profile `emailAddress` as `oauth-fixture@test.local`. Keep the Google authorize URL solely for validating its format; it is never followed. The provider must support revoke and assert **four successful code exchanges and two successful revocations** for this collection (unlink and admin cascade-delete), without logging tokens, codes or verifiers. The harness should fail if those independent counts differ.

Provide exactly these five environment values in a temporary Newman environment JSON file, outside the repository and accessible only to the fixture runner:

```json
{
  "name": "Disposable acceptance only",
  "values": [
    {"key":"baseUrl","value":"https://localhost:<fixture-port>","enabled":true},
    {"key":"fixtureMode","value":"disposable-loopback","enabled":true},
    {"key":"oauthFixture","value":"1","enabled":true},
    {"key":"adminUsername","value":"<disposable-admin>","enabled":true},
    {"key":"adminPassword","value":"<random-fixture-password>","enabled":true}
  ]
}
```

The guard rejects non-loopback addresses, HTTP, URL credentials/paths, ports at or below 1024, port 8443, missing fixture/OAuth markers or missing admin credentials. The initial configuration must allow registration, App Password, OAuth and sending, have maintenance off and a positive Gmail quota. The collection generates its own independent user names/passwords and saves runtime tokens only in memory; it does not use existing global variables, environments, browser profiles or credential files.

## Execute

The standalone command below uses an already available Newman executable; it does not install anything. Alternatively, `scripts/test-client-e2e.ps1 -Java21Home <JDK21> -DatabaseEngine MySQL -RunPostman` installs pinned Newman 6.2.1 only inside its disposable temporary directory, runs the Java E2E suite, passes the fixture variables in memory, and writes sanitized counters under `build/acceptance/`. This path needs npm registry access but never changes an existing Postman profile or global npm installation. Export the fixture's certificate in PEM form (for example, `keytool -exportcert -rfc` against the fixture's generated PKCS12). In the isolated runner process, clear inherited HTTP/HTTPS/ALL proxy settings and Node TLS override/extra-CA environment variables; ensure loopback is not proxied. Never use `--insecure` or `NODE_TLS_REJECT_UNAUTHORIZED=0`.

From the `Mail Server` directory, with `$fixtureEnvFile` and `$fixturePemFile` set to the disposable harness paths:

```powershell
& newman run 'docs/http/acceptance.postman_collection.json' `
  --environment $fixtureEnvFile `
  --folder 'Core acceptance' `
  --ssl-extra-ca-certs $fixturePemFile `
  --ignore-redirects --bail failure --timeout-request 15000 --silent
if ($LASTEXITCODE -ne 0) { throw 'Disposable Newman acceptance failed.' }
```

The collection never opens the returned Google authorize URL or exchanges a real Google code. Callback cases use an invalid state, explicitly denied consent, or the synthetic PKCE-verified code accepted only by the local provider. App Password cases are invalid, disabled, duplicate or over quota before probing; the send case uses an owned fixture mailbox with sending disabled. Do not use verbose, JSON, JUnit or other raw reporters, export an environment/collection after execution, or print HTTP bodies, headers or URLs with poll/state values. `--silent` preserves the failure exit code without exposing credentials; report only the exit status or sanitized aggregate counters from the programmatic Newman API. Do not put the admin password on a command line.

The collection restores all original setting **values** and deletes only the users it created on a successful run. Audit rows and setting-update metadata remain in the disposable database. A failed assertion stops the sequence, so restoration is not guaranteed after a failure: the harness must always stop its child processes and destroy its own isolated database, certificate and environment file in `finally`, regardless of Newman outcome. Never run this collection in parallel with other tests sharing the fixture.

## Scope of evidence

- Registration validation, role spoofing, case-insensitive duplicate, login by username/email, generic 401, JWT metadata, password change and old password rejection, forced change, role and maintenance gates, missing/invalid bearer.
- Admin target create/list/detail/lock/unlock/role/reset/delete, including self-action protection exercised on the collection-created promoted target, without mutating the fixture admin.
- Settings whitelist, invalid/atomic updates, positive numeric values, immediate public flags, restoration, login rate limiting and Retry-After; registration, OAuth and App Password feature rejection.
- Mailbox list isolation, actual foreign linked-mailbox and admin denial, missing-ID guards, quota and disabled send on an owned mailbox; OAuth authorization URL, PKCE, state/poll ownership, denied and replayed sessions, successful local callback/profile lookup, duplicate CREATE, explicit UPSERT, separate owner rows, unlink and admin cascade-delete. Structured HTTP errors and persisted, filtered audit rows/CSV reject known test secrets and unexpected credential fields.
- Live IMAPS/SMTP, real Google consent/token exchange/revoke, schema migrations, TLS certificate generation and last-admin concurrency are **not** established by this collection. Use the separately scoped Java/GreenMail/MySQL suites or manual Google verification for those criteria. This is automated HTTP evidence, not a claim of manual Postman or production acceptance.

## Manual session từng request

For operator-selected HTTP observations, use the bounded `-ManualSession` mode (see [dev-setup.md](../dev-setup.md)). `-ManualPostman` optionally installs pinned Newman in that session's private temp root and uses its actual runtime for individual requests without collection assertions; it needs npm access. Without it the helper uses verified native HTTPS. Multipart is native HTTPS in both modes. The supplied `auth.http`, `admin.http` and `mail.http` are illustrative request shapes only: all values are placeholders, not a bulk suite. Do not import them into personal Postman/IDE environments or persist request/response history containing fixture secrets.

From Mail Server in the operator's second PowerShell, once the harness reports ready, read only the pointer and give the helper the manifest **path**, not its contents:

```powershell
$pointer = Get-Content 'build/acceptance/manual-session.json' -Raw | ConvertFrom-Json
$manifest = $pointer.manifest
'' | & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4333 public config' none GET '/api/public/config'
'' | & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4311 own profile' http_manual GET '/api/auth/me'
```

The helper accepts `request <manifest> <task-label> <actor> <METHOD> <relative-path> [save-alias|-] [Accept]` and reads the optional JSON request body from stdin. Actors `none` and `invalid` exercise unauthenticated/invalid bearer; seeded actors `admin`, `http_manual`, `ui_other`, `ui_manual` use fixture tokens maintained by the helper. Use saved aliases and supported fixture references without printing their contents. Avoid shell aliases such as `Set`; submit the actual body shape and confirm public config before exercising a disabled feature. Example correct media and settings validation:

```powershell
'' | & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4337 export CSV' admin GET '/api/admin/logs/export?action=MAIL_DELETE' '-' 'text/csv'
@{settings=@{MAX_GMAIL_PER_USER='0'}} | ConvertTo-Json -Compress |
  & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4335 invalid quota' admin PUT '/api/admin/settings'
```

OAuth local-only denial, no real Google URL or browser consent:

```powershell
'' | & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4316 authorize' http_manual GET '/api/oauth2/authorize-url' 'denySession'
& node.exe .\scripts\manual-http.cjs callback $manifest 'AM-4317 deny local consent' http_manual 'denySession' deny
'' | & node.exe .\scripts\manual-http.cjs request $manifest 'AM-4317 terminal poll' http_manual GET '/api/oauth2/link/poll?pollToken={{saved.denySession.pollToken}}'
```

Use a fresh session for each distinct authorization attempt; terminal poll is consumed. The helper sets Accept HTML for callback; request mode defaults JSON, so explicit HTML/CSV is necessary for those positive cases. To test disabled registration use a valid fresh registration body, not empty stdin; to test disabled App Password use POST link, not GET mailbox list. Settings changes must be reverted before other operators use the fixture; coordinate with Swing operator and never run collections concurrently. For multipart, the helper accepts `multipart <manifest> <task-label> <actor> /api/mails/send legacy|modern` with JSON stdin containing the corresponding form fields/payload plus optional `fileBytes` and `fileName` to generate deterministic bytes. Send only to the isolated mailbox, not real addresses.

Observe sanitized status/body summary, content type, disposition, decoded byte counts/hash and transport; do not mark the case by request label alone. During the session, evidence is at `Join-Path $pointer.root 'manual-http-observed.ndjson'`; after normal stop, use `build/acceptance/manual-http-observed.ndjson`. To stop only the active fixture you own, after all operators finish, signal the marker:

```powershell
New-Item -ItemType File -Path (Join-Path $pointer.root 'stop-manual') -Force | Out-Null
```

The harness checks lifetime and child ownership, copies sanitized evidence, and cleans its disposable resources; do not copy or print private manifest/state. Keep a separate snapshot for actual observations, including failures and retests, and distinguish agent-operated manual, automated assertion results, human signoff and real Google/Gmail.
