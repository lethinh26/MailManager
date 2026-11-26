// Test-only provider; never contacts Google and never logs credentials.
'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const port = Number(process.argv[2]);
const redirect = new URL(process.env.GOOGLE_REDIRECT_URI || 'http://invalid');
if (process.env.MAILAPP_OAUTH_FIXTURE !== '1' || !Number.isInteger(port)
    || port < 20000 || port >= 40000 || redirect.protocol !== 'https:'
    || redirect.hostname !== 'localhost' || redirect.port === '8443'
    || !redirect.port || redirect.pathname !== '/api/oauth2/callback') {
    throw new Error('Refusing non-disposable OAuth fixture.');
}
const email = 'oauth-fixture@test.local';
const codes = new Set();
const accesses = new Map();
const refreshes = new Map();
const stats = { exchanges: 0, userinfo: 0, profiles: 0, revokes: 0, rejected: 0, refreshRejected: 0 };
const faultRoot = fs.realpathSync(process.cwd());
const faultsEnabled = process.env.MAILAPP_MANUAL_FAULTS === '1';
if (faultsEnabled && (path.dirname(faultRoot).toLowerCase() !== fs.realpathSync(os.tmpdir()).toLowerCase()
    || !/^mailapp-e2e-[a-f0-9]{32}$/.test(path.basename(faultRoot))))
    throw new Error('Fault controls require disposable fixture directory.');
const faultPath = path.join(faultRoot, 'oauth-fixture-fault.txt');
function fault() {
    if (!faultsEnabled || !fs.existsSync(faultPath)) return 'normal';
    const st = fs.lstatSync(faultPath);
    if (!st.isFile() || st.isSymbolicLink() || st.size > 64) throw new Error('Invalid fault control');
    const v = fs.readFileSync(faultPath,'ascii').trim();
    if (!['normal','missing-scope','profile-mismatch','reject-revoke','reject-refresh'].includes(v))
        throw new Error('Invalid fixture fault');
    return v;
}
const random = () => crypto.randomBytes(32).toString('base64url');
function reply(res, status, value) {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(value));
}
function reject(res) {
    stats.rejected++;
    reply(res, 400, { error: 'invalid_fixture_request' });
}
async function form(req) {
    if (!/^application\/x-www-form-urlencoded(?:;|$)/i.test(req.headers['content-type'] || '')) {
        throw new Error('invalid content type');
    }
    let data = '';
    for await (const part of req) {
        data += part.toString('utf8');
        if (Buffer.byteLength(data) > 8192) throw new Error('oversize');
    }
    return new URLSearchParams(data);
}
const server = http.createServer(async (req, res) => {
    try {
        if (req.method === 'GET' && req.url === '/status') return reply(res, 200, stats);
        if (req.method === 'POST' && req.url === '/token') {
            const p = await form(req);
            // A real refresh request from the disposable application's OAuth
            // flow, not an injected token. Simulate provider revocation without
            // changing any stored credential or exposing it to the operator.
            if (p.get('grant_type') === 'refresh_token' && fault() === 'reject-refresh') {
                if (p.get('client_id') !== 'mailapp-e2e.invalid'
                    || p.get('client_secret') !== 'local-test-only'
                    || !refreshes.has(p.get('refresh_token'))) return reject(res);
                stats.refreshRejected++;
                return reply(res, 400, { error: 'invalid_grant', error_description: 'F-provider-error-detail-canary' });
            }
            const code = p.get('code') || '';
            const verifier = p.get('code_verifier') || '';
            const match = /^fixture-([A-Za-z0-9_-]{43})$/.exec(code);
            if (!match || codes.has(code) || !/^[A-Za-z0-9._~-]{43,128}$/.test(verifier)
                || crypto.createHash('sha256').update(verifier).digest('base64url') !== match[1]
                || p.get('client_id') !== 'mailapp-e2e.invalid'
                || p.get('client_secret') !== 'local-test-only'
                || p.get('redirect_uri') !== redirect.href
                || p.get('grant_type') !== 'authorization_code') return reject(res);
            codes.add(code);
            const access = random(), refresh = random();
            const mode = fault();
            const identity = { revoked: false, profileMismatch: mode === 'profile-mismatch' };
            accesses.set(access, identity);
            refreshes.set(refresh, identity);
            stats.exchanges++;
            return reply(res, 200, { access_token: access, refresh_token: refresh,
                token_type: 'Bearer', expires_in: mode === 'reject-refresh' ? 1 : 3600,
                scope: mode === 'missing-scope' ? 'openid email profile' : 'https://mail.google.com/ openid email profile' });
        }
        if (req.method === 'POST' && req.url === '/revoke') {
            const p = await form(req);
            const identity = refreshes.get(p.get('token'));
            if (!identity || identity.revoked) return reject(res);
            if (fault() === 'reject-revoke') {
                stats.rejected++;
                return reply(res, 503, { error: 'fixture_revoke_unavailable' });
            }
            identity.revoked = true;
            stats.revokes++;
            return reply(res, 200, {});
        }
        if (req.method === 'GET' && ['/userinfo', '/profile'].includes(req.url)) {
            const auth = /^Bearer ([A-Za-z0-9_-]+)$/.exec(req.headers.authorization || '');
            const identity = auth && accesses.get(auth[1]);
            if (!identity || identity.revoked) return reject(res);
            if (req.url === '/userinfo') {
                stats.userinfo++;
                return reply(res, 200, { email, email_verified: true, verified_email: true, name: 'OAuth Fixture' });
            }
            stats.profiles++;
            return reply(res, 200, { emailAddress: identity.profileMismatch ? 'mismatch-fixture@test.local' : email });
        }
        return reject(res);
    } catch (_) {
        if (!res.headersSent) reject(res);
        else res.end();
    }
});
server.requestTimeout = 10000;
server.headersTimeout = 5000;
server.listen(port, '127.0.0.1');
