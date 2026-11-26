// Newman runner for test-client-e2e.ps1. No persisted tokens, passwords or raw responses.
'use strict';
const fs = require('node:fs');
const path = require('node:path');

const [moduleDir, collectionFile, certificate, output] = process.argv.slice(2);
const baseUrl = process.env.MAILAPP_E2E_URL;
const password = process.env.MAILAPP_E2E_CHANGED_ADMIN_PASSWORD;
const url = new URL(baseUrl || 'http://invalid');
if (process.env.MAILAPP_E2E_DISPOSABLE !== '1' || process.env.MAILAPP_E2E !== '1'
    || url.protocol !== 'https:' || !['localhost', '127.0.0.1'].includes(url.hostname)
    || !url.port || url.port === '8443' || url.username || url.password || url.pathname !== '/'
    || url.search || url.hash || !password || !fs.existsSync(certificate)) {
    throw new Error('Refusing non-disposable Newman target.');
}
// Never route fixture requests via an inherited outbound proxy.
for (const key of Object.keys(process.env)) {
    if (/^(http_proxy|https_proxy|all_proxy|node_tls_reject_unauthorized|node_extra_ca_certs)$/i.test(key)) delete process.env[key];
}
process.env.NO_PROXY = 'localhost,127.0.0.1';

const collection = JSON.parse(fs.readFileSync(collectionFile, 'utf8'));
const newman = require(path.resolve(moduleDir));
const environment = { name: 'disposable-loopback', values: [
    { key: 'baseUrl', value: baseUrl, enabled: true },
    { key: 'fixtureMode', value: 'disposable-loopback', enabled: true },
    { key: 'oauthFixture', value: '1', enabled: true },
    { key: 'adminUsername', value: 'admin', enabled: true },
    { key: 'adminPassword', value: password, enabled: true, type: 'secret' }
] };

// No CLI/JSON reporter: they can persist request bodies, tokens and responses.
newman.run({ collection, environment, reporters: [], silent: true,
    sslExtraCaCerts: path.resolve(certificate), insecure: false, ignoreRedirects: true,
    timeout: 240000, timeoutRequest: 15000, timeoutScript: 10000, bail: ['failure']
}, (error, summary) => {
    if (error || !summary || !summary.run) {
        console.error('Newman failed before producing acceptance results.');
        process.exitCode = 1;
        return;
    }
    const { stats, executions, failures } = summary.run;
    const report = {
        executedAt: new Date().toISOString(), database: process.env.MAILAPP_E2E_DATABASE_ENGINE,
        newmanVersion: require(path.resolve(moduleDir, 'package.json')).version,
        stats: { requests: stats.requests, assertions: stats.assertions,
            testScripts: stats.testScripts, prerequestScripts: stats.prerequestScripts },
        cases: executions.map(e => ({ name: e.item.name,
            status: e.response ? e.response.code : null,
            assertions: (e.assertions || []).map(a => ({ name: a.assertion,
                result: a.skipped ? 'SKIPPED' : a.error ? 'FAIL' : 'PASS' })) })),
        failures: failures.map(f => ({ item: f.source && f.source.name,
            kind: f.error && f.error.name }))
    };
    // Only static case/assertion names, statuses and aggregate counts; no headers,
    // raw assertion error messages, request URLs, body contents or credentials.
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
    console.log(`Newman: ${stats.requests.total} requests, ${stats.assertions.total} assertions, ${failures.length} failures, ${stats.assertions.pending || 0} pending.`);
    for (const f of report.failures) console.error(`${f.item || 'collection'}: ${f.kind || 'failure'}`);
    if (failures.length || stats.requests.failed || stats.assertions.failed || stats.assertions.pending
        || !stats.requests.total || !stats.assertions.total) process.exitCode = 1;
});
