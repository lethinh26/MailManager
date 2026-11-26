// Interactive, one-request-at-a-time HTTP client for the disposable manual fixture.
// No test assertions, no raw credentials, tokens, or response bodies saved to reports.
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const crypto = require('node:crypto');
const os = require('node:os');
const argv = process.argv.slice(2);
const [mode, manifestFile] = argv;
const manifestPath = path.resolve(manifestFile || '');
const root = path.dirname(manifestPath);
const temp = path.resolve(os.tmpdir());
if (path.dirname(root).toLowerCase() !== temp.toLowerCase()
    || !/^mailapp-e2e-[a-f0-9]{32}$/.test(path.basename(root))
    || path.basename(manifestPath) !== 'manual-private.json')
    throw new Error('Refusing non-fixture manifest.');
const fixture = JSON.parse(fs.readFileSync(manifestPath, 'utf8').replace(/^\uFEFF/, ''));
const origin = new URL(fixture.baseUrl);
if (fixture.disposable !== true || origin.protocol !== 'https:' || origin.hostname !== 'localhost'
    || Number(origin.port) <= 1024 || origin.port === '8443' || origin.pathname !== '/'
    || origin.search || origin.hash || origin.username || origin.password
    || path.resolve(fixture.root) !== root || path.resolve(fixture.pem) !== path.join(root,'server.pem')
    || fs.existsSync(fixture.stopFile)) throw new Error('Invalid fixture scope.');
const ca = fs.readFileSync(fixture.pem);
// When available, use the real Postman runtime for individually chosen HTTP
// requests. No collection test scripts, assertions or automatic scenario loops.
// Binary multipart is kept on native HTTPS to preserve exact request bytes.
const postmanModule = path.join(root,'manual-postman','node_modules','newman');
const hasPostman = fs.existsSync(path.join(postmanModule,'package.json'));
for (const k of Object.keys(process.env)) {
    if (/^(http_proxy|https_proxy|all_proxy|node_tls_reject_unauthorized|node_extra_ca_certs)$/i.test(k)) delete process.env[k];
}
process.env.NO_PROXY = 'localhost,127.0.0.1';
const stateFile = path.join(root,'manual-http-private.json');
let state = fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile,'utf8')) : { tokens:{}, saved:{} };
const save = () => fs.writeFileSync(stateFile, JSON.stringify(state));
const secrets = [fixture.adminInitialPassword, fixture.adminPassword, ...fixture.users.flatMap(u=>[u.password,u.appPassword])];
const sensitive = /password|token|authorization|secret|credential|cookie|verifier|challenge|state|authorizeUrl/i;
function collectSecrets(v,out) {
    if (Array.isArray(v)) {for(const x of v) collectSecrets(x,out);}
    else if(v&&typeof v==='object') for(const [k,x] of Object.entries(v)) {
        if(sensitive.test(k)&&typeof x==='string'&&x.length>7) out.push(x);
        else if(x&&typeof x==='object') collectSecrets(x,out);
    }
}
function redact(v, depth=0) {
    if (depth>12) return '[nested]';
    if (typeof v==='string') {
        let s=v; const privateValues=[...secrets,...Object.values(state.tokens)];
        collectSecrets(state.saved,privateValues);
        for (const p of privateValues.filter(Boolean)) s=s.split(p).join('[redacted]');
        return s.length>700?s.slice(0,700)+'...':s;
    }
    if (Array.isArray(v)) return v.length>12 ? {count:v.length,first:v.slice(0,3).map(x=>redact(x,depth+1)),last:redact(v.at(-1),depth+1)} : v.map(x=>redact(x,depth+1));
    if (v&&typeof v==='object') return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,sensitive.test(k)&&!['mustChangePassword','hasAppPassword','appPasswordLinkEnabled','gmailByAppPassword','tokenType','tokenExpiresAt','challengeShape','statePresent','distinctStateAndPoll'].includes(k)?(x==null?null:'[present]'):redact(x,depth+1)]));
    return v;
}
function expand(s) {
    return s.replace(/\{\{([\w.]+)\}\}/g, (_,key)=>{
        const keys=key.split('.');
        const value=keys.reduce((v,k)=>v?.[k],{fixture,saved:state.saved});
        if (value==null || typeof value==='object') throw new Error('Missing scalar reference '+key);
        return String(value);
    });
}
function responseValue(status, headers, bytes, transport) {
    const length=bytes.length;
    const type=headers['content-type']||'';
    let value;
    if (type.includes('json')) {try {value=JSON.parse(bytes.toString('utf8'));} catch {value={invalidJson:true}}}
    else if(type.includes('text/html')) value={html:true,bytes:length,text:bytes.toString('utf8').replace(/<style[\s\S]*?<\/style>/gi,'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim().slice(0,350)};
    else if(type.includes('text/csv')) value={csv:true,bytes:length,lines:bytes.toString('utf8').split(/\r?\n/).length,header:bytes.toString('utf8').split(/\r?\n/)[0]};
    else value={bytes:length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
    return {transport,status,type,retryAfter:headers['retry-after'],disposition:headers['content-disposition'],
        cacheControl:headers['cache-control'],contentLength:headers['content-length'],value};
}
function request(actor,method,relative,body,headers={}) {
    if (!['GET','POST','PUT','PATCH','DELETE'].includes(method) || !relative.startsWith('/')
        || relative.startsWith('//') || relative.includes('\\') || relative.includes('#'))
        throw new Error('Invalid fixture request');
    const target = new URL(relative,origin);
    if (target.origin!==origin.origin || !/^\/(api\/|actuator\/health$)/.test(target.pathname))
        throw new Error('Non-fixture request');
    const data=body==null?null:Buffer.isBuffer(body)?body:Buffer.from(JSON.stringify(body));
    const token=actor==='none'?null:actor==='invalid'?'not-a-valid-token':state.tokens[actor];
    if (actor!=='none'&&!token) throw new Error('No login for actor '+actor);
    const requestHeaders={Accept:'application/json',...(token?{Authorization:'Bearer '+token}:{}),
        ...(data?{'Content-Type':'application/json','Content-Length':data.length}:{}),...headers};
    if (hasPostman && !Buffer.isBuffer(body)) {
        const newman=require(postmanModule);
        const collection={info:{name:'Manual fixture single request',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
            item:[{name:'Operator-selected request',request:{method,url:target.href,
                header:Object.entries(requestHeaders).map(([key,value])=>({key,value:String(value)})),
                ...(data?{body:{mode:'raw',raw:data.toString('utf8')}}:{})}}]};
        return new Promise((resolve,reject)=>{
            newman.run({collection,reporters:[],silent:true,sslExtraCaCerts:fixture.pem,
                insecure:false,ignoreRedirects:true,timeout:45000,timeoutRequest:30000,
                timeoutScript:5000,bail:['failure']},(err,summary)=>{
                // Never fall back to another transport after an uncertain mutation.
                const execution=summary?.run?.executions?.[0];
                if(err||summary?.run?.failures?.length||!execution?.response)
                    return reject(new Error('Postman transport failed; no automatic retry.'));
                const res=execution.response;
                const responseHeaders=Object.fromEntries(res.headers.all().map(h=>[h.key.toLowerCase(),h.value]));
                const bytes=Buffer.from(res.stream||[]);
                if(bytes.length>70*1024*1024) return reject(new Error('fixture response limit'));
                resolve(responseValue(res.code,responseHeaders,bytes,'Postman/Newman single request'));
            });
        });
    }
    return new Promise((resolve,reject)=>{
        const req=https.request(target,{method,ca,rejectUnauthorized:true,agent:false,
            headers:requestHeaders},res=>{
            const chunks=[]; let length=0;
            res.on('data',c=>{length+=c.length;if(length>70*1024*1024) req.destroy(new Error('fixture response limit'));else chunks.push(c)});
            res.on('end',()=>{
                resolve(responseValue(res.statusCode,res.headers,Buffer.concat(chunks),'HTTPS direct'));
            });
            res.on('error',reject);
        });
        req.setTimeout(30000,()=>req.destroy(new Error('fixture timeout')));
        req.on('error',reject);req.end(data);
    });
}
async function login(name,password,actor=name) {
    const r=await request('none','POST','/api/auth/login',{username:name,password});
    if(r.status===200&&r.value?.accessToken) {state.tokens[actor]=r.value.accessToken; save();}
    return r;
}
function record(task,actor,method,url,r) {
    if (r.value && typeof r.value.authorizeUrl === 'string') {
        const authorization = new URL(r.value.authorizeUrl);
        const q = authorization.searchParams;
        r.authorizeShape = {
            https: authorization.protocol === 'https:',
            responseTypeCode: q.get('response_type') === 'code',
            gmailScope: (q.get('scope') || '').split(/\s+/).includes('https://mail.google.com/'),
            offline: q.get('access_type') === 'offline',
            redirectMatchesFixture: q.get('redirect_uri') === new URL('/api/oauth2/callback',origin).href,
            pkceS256: q.get('code_challenge_method') === 'S256',
            challengeShape: /^[A-Za-z0-9_-]{43}$/.test(q.get('code_challenge') || ''),
            statePresent: Boolean(q.get('state')),
            pollPresent: Boolean(r.value.pollToken),
            distinctStateAndPoll: q.get('state') !== r.value.pollToken
        };
    }
    const clean={time:new Date().toISOString(),task,actor,method,path:url.split('?')[0],queryKeys:[...new URL(url,origin).searchParams.keys()],...redact(r)};
    fs.appendFileSync(path.join(root,'manual-http-observed.ndjson'),JSON.stringify(clean)+'\n');
    console.log(JSON.stringify(clean,null,2));
}
function requireStatus(r,code,label) { if(r.status!==code) throw new Error('Fixture setup '+label+' status '+r.status+' '+(r.value?.code||'')); }
async function seed() {
    const init=await login('admin',fixture.adminInitialPassword,'admin');requireStatus(init,200,'admin login');
    const change=await request('admin','POST','/api/auth/change-password',{currentPassword:fixture.adminInitialPassword,newPassword:fixture.adminPassword});
    requireStatus(change,200,'admin change');
    requireStatus(await login('admin',fixture.adminPassword,'admin'),200,'changed admin');
    for(const u of fixture.users) {
        const reg=await request('none','POST','/api/auth/register',{username:u.username,email:u.email,password:u.password,fullName:'Manual Fixture '+u.username,role:'USER'});
        requireStatus(reg,201,'register');u.id=reg.value.id;
        requireStatus(await login(u.username,u.password),200,'login');
        if(u.username!=='ui_manual') {
            const link=await request(u.username,'POST','/api/gmail-accounts/app-password',{email:u.email,appPassword:u.appPassword,intent:'UPSERT'});
            requireStatus(link,201,'link');u.linkedId=link.value.id;
        }
    }
    fs.writeFileSync(manifestPath,JSON.stringify(fixture,null,2));
    save();console.log('Disposable users, credentials and mailbox links seeded.');
}
async function main() {
    if(mode==='seed') return seed();
    if(mode==='login') {
        const [task,actor,username,password]=argv.slice(2);
        const r=await login(expand(username),expand(password),actor);
        record(task,actor,'POST','/api/auth/login',r);return;
    }
    if(mode==='request') {
        const [task,actor,method,url,alias,accept,contentType]=argv.slice(2);
        const relative=expand(url);
        const input=fs.readFileSync(0,'utf8').trim();
        const body=input?JSON.parse(expand(input)):null;
        const startedAt=new Date().toISOString();
        const r=await request(actor,method,relative,body,{
            ...(accept?{Accept:accept}:{}),
            ...(contentType?{'Content-Type':contentType}:{})
        });
        r.startedAt=startedAt;
        if(alias&&alias!=='-') {state.saved[alias]=r.value;save();}
        record(task,actor,method,relative,r);return;
    }
    if(mode==='callback') {
        const [task,actor,alias,how,accept='text/html']=argv.slice(2);
        const a=state.saved[alias];
        const auth=new URL(a.authorizeUrl||a.url);
        const q=new URLSearchParams({state:auth.searchParams.get('state')});
        if(how==='deny') q.set('error','access_denied');
        else q.set('code','fixture-'+auth.searchParams.get('code_challenge'));
        const r=await request('none','GET','/api/oauth2/callback?'+q,null,{Accept:accept});
        record(task,actor,'GET','/api/oauth2/callback',r);return;
    }
    if(mode==='tls-check') {
        const task=argv[2];
        const result=await new Promise((resolve,reject)=>{
            const req=https.get(new URL('/actuator/health',origin),{rejectUnauthorized:true,agent:false},res=>{
                res.resume();resolve({transport:'HTTPS default trust',status:res.statusCode,value:{certificateAccepted:true}});
            });
            req.setTimeout(10000,()=>req.destroy(new Error('fixture timeout')));
            req.on('error',e=>{
                if(['DEPTH_ZERO_SELF_SIGNED_CERT','SELF_SIGNED_CERT_IN_CHAIN','UNABLE_TO_VERIFY_LEAF_SIGNATURE','UNABLE_TO_GET_ISSUER_CERT_LOCALLY'].includes(e.code))
                    resolve({transport:'HTTPS default trust',status:null,value:{certificateAccepted:false,errorCode:e.code}});
                else reject(e);
            });
        });
        record(task,'none','GET','/actuator/health',result);return;
    }
    if(mode==='tls-info') {
        const result=await new Promise((resolve,reject)=>{
            const req=https.get(new URL('/actuator/health',origin),{ca,rejectUnauthorized:true,agent:false},res=>{
                const info={transport:'HTTPS negotiated TLS',status:res.statusCode,value:{
                    authorized:res.socket.authorized,protocol:res.socket.getProtocol(),cipher:res.socket.getCipher()?.name}};
                res.resume();res.on('end',()=>resolve(info));
            });
            req.setTimeout(10000,()=>req.destroy(new Error('fixture timeout')));
            req.on('error',reject);
        });
        record(argv[2],'none','GET','/actuator/health',result);return;
    }
    if(mode==='provider-status') {
        if(fixture.manualFaults!==true || !Number.isInteger(fixture.oauthPort) || fixture.oauthPort<20000 || fixture.oauthPort>=40000)
            throw new Error('Explicit disposable provider required');
        const res=await fetch('http://127.0.0.1:'+fixture.oauthPort+'/status',{redirect:'error',signal:AbortSignal.timeout(5000)});
        if(!res.ok) throw new Error('Provider status unavailable');
        const stats=await res.json();
        const keys=['exchanges','userinfo','profiles','revokes','rejected','refreshRejected'];
        if(keys.some(k=>!Number.isSafeInteger(stats[k])||stats[k]<0)) throw new Error('Invalid fixture counters');
        record(argv[2],'fixture','GET','/fixture/oauth-status',{transport:'Local fixture counters',status:res.status,value:Object.fromEntries(keys.map(k=>[k,stats[k]]))});
        return;
    }
    if(mode==='audit-fault') {
        if(fixture.manualAuditFaults!==true || fixture.databaseEngine!=='MySQL')
            throw new Error('Explicit disposable audit fault required');
        const [task,action]=argv.slice(2);
        if(!['reject-audit','restore-audit','audit-status','privacy-status',
            'reject-intent','restore-intent','reject-outcome','restore-outcome',
            'reject-drain','restore-drain','outbox-status','restart-server','recovery-privacy-status',
            'delay-role','role-overlap-status','restore-role','provider-error-privacy'].includes(action))
            throw new Error('Unsupported audit fault');
        if(['delay-role','role-overlap-status','restore-role'].includes(action)&&fixture.manualAdminOverlap!==true)
            throw new Error('Explicit admin overlap fixture required');
        const inbox=path.join(root,'audit-fixture-command.txt'),ack=path.join(root,'audit-fixture-ack.txt');
        if(fs.existsSync(inbox)||fs.existsSync(ack)) throw new Error('Prior audit fault outstanding; do not retry');
        fs.writeFileSync(inbox+'.next',action,{encoding:'ascii',flag:'wx'});
        fs.renameSync(inbox+'.next',inbox);
        const until=Date.now()+(action==='restart-server'?90000:15000);
        while(!fs.existsSync(ack)&&Date.now()<until) await new Promise(r=>setTimeout(r,100));
        if(!fs.existsSync(ack)) throw new Error('Audit acknowledgement missing; do not retry');
        const outcome=fs.readFileSync(ack,'ascii').trim();
        fs.unlinkSync(ack);
        const expected=action==='privacy-status'
            ? /^privacy-status OK subject=\d+ body=\d+ path=\d+ sql=\d+ auditFailures=\d+ sendAuditSuccess=\d+$/
            : action==='outbox-status'
            ? /^outbox-status OK prepared=\d+ confirmed=\d+ uncertain=\d+ sent=\d+ deleted=\d+ flags=\d+ ambiguous=\d+$/
            : action==='recovery-privacy-status'
            ? /^recovery-privacy-status OK subject=\d+ body=\d+ recipient=\d+ filename=\d+ path=\d+ sql=\d+ outbox=\d+ audit=\d+$/
            : action==='provider-error-privacy'
            ? /^provider-error-privacy OK log=\d+ outbox=\d+ audit=\d+$/
            : action==='role-overlap-status'
            ? /^role-overlap-status OK waits=\d+$/
            : new RegExp('^'+action+' OK(?: rows=\\d+ mailPageSize=\\d+ rejecting=(?:True|False))?$');
        if(!expected.test(outcome))
            throw new Error('Unexpected audit acknowledgement');
        record(task,'fixture','CONTROL','/fixture/audit-fault',{transport:'Fixture local control',status:null,value:{action,outcome}});
        return;
    }
    if(mode==='lease-status') {
        if((fixture.manualLeases!==true && fixture.manualFaults!==true) || !Number.isSafeInteger(fixture.serverPid) || fixture.serverPid<=0
            || [fixture.imapsPort,fixture.smtpPort].some(p=>!Number.isInteger(p)||p<20000||p>=40000))
            throw new Error('Explicit lease fixture required');
        // Aggregate only owned Java process -> loopback mail listener sockets.
        // Never output raw connection tables or inspect process command lines.
        const table=require('node:child_process').execFileSync('netstat.exe',['-ano','-p','tcp'],{encoding:'utf8',timeout:5000,windowsHide:true});
        const counts={imaps:0,smtp:0};
        for(const line of table.split(/\r?\n/)) {
            const [proto,local,remote,state,pid]=line.trim().split(/\s+/);
            if(proto==='TCP'&&state==='ESTABLISHED'&&Number(pid)===fixture.serverPid&&local.startsWith('127.0.0.1:')){
                if(remote==='127.0.0.1:'+fixture.imapsPort)counts.imaps++;
                if(remote==='127.0.0.1:'+fixture.smtpPort)counts.smtp++;
            }
        }
        record(argv[2],'fixture','CONTROL','/fixture/lease-status',{transport:'Owned loopback socket aggregates',status:null,value:counts});
        return;
    }
    if(mode==='mail-fault' || mode==='oauth-fault') {
        if(fixture.manualFaults!==true) throw new Error('Explicit -ManualFaults required');
        const [task,action]=argv.slice(2);
        let outcome;
        if(mode==='mail-fault') {
            if(!['pause-imaps','resume-imaps','pause-smtp','resume-smtp','stall-imaps','stall-smtp','status-imaps','status-smtp',
                'deny-mail-auth','restore-mail-auth','fail-after-delivery','provider-fault-status'].includes(action))
                throw new Error('Unsupported fixture mail fault');
            if(/^(stall|status)-/.test(action) && fixture.manualStalls!==true)
                throw new Error('Explicit disposable timeout fixture required');
            const inbox=path.join(root,'mail-fixture-command.txt'), ack=path.join(root,'mail-fixture-ack.txt');
            if(fs.existsSync(inbox)||fs.existsSync(ack)) throw new Error('Prior fault command outstanding; inspect manually');
            const pending=inbox+'.next';
            fs.writeFileSync(pending,action,{encoding:'ascii',flag:'wx'});
            fs.renameSync(pending,inbox);
            const until=Date.now()+15000;
            while(!fs.existsSync(ack)&&Date.now()<until) await new Promise(r=>setTimeout(r,100));
            if(!fs.existsSync(ack)) throw new Error('Fault acknowledgement missing; do not retry blindly');
            outcome=fs.readFileSync(ack,'ascii').trim();
            fs.unlinkSync(ack);
            if(action==='provider-fault-status' && !/^provider-fault-status OK authRejected=\d+ deliveries=\d+ postDeliveryFailures=\d+ authDenied=(true|false) armed=(true|false)$/.test(outcome))
                throw new Error('Unexpected provider aggregate acknowledgement');
        } else {
            if(!['normal','missing-scope','profile-mismatch','reject-revoke','reject-refresh'].includes(action))
                throw new Error('Unsupported fixture OAuth fault');
            const target=path.join(root,'oauth-fixture-fault.txt');
            if(fs.existsSync(target)&&!fs.lstatSync(target).isFile()) throw new Error('Invalid fault control path');
            fs.writeFileSync(target,action,'ascii');
            outcome='Provider fault selected; verify next actual request';
        }
        record(task,'fixture','CONTROL','/fixture/'+mode,{transport:'Fixture local control',status:null,value:{action,outcome}});
        if(outcome.startsWith('ERROR')) throw new Error('Fixture control failed');
        return;
    }
    if(mode==='multipart') {
        const [task,actor,url,kind='legacy']=argv.slice(2);
        const data=JSON.parse(expand(fs.readFileSync(0,'utf8').replace(/^\uFEFF/,'').trim()));
        const boundary='fixture-'+crypto.randomBytes(12).toString('hex');
        let parts=[];
        function part(name,content,type,filename) {
            parts.push(Buffer.from('--'+boundary+'\r\nContent-Disposition: form-data; name="'+name+'"'+(filename?'; filename="'+filename+'"':'')+'\r\n'+(type?'Content-Type: '+type+'\r\n':'')+'\r\n'),Buffer.from(content),Buffer.from('\r\n'));
        }
        const {fileBytes,fileName,fixtureFiles,...fields}=data;
        if(kind==='modern') part('payload',JSON.stringify(fields),'application/json');
        else for(const [k,v] of Object.entries(fields)) part(k,Array.isArray(v)?v.join(','):String(v));
        if(fileBytes!==undefined) part(kind==='modern'?'attachments':'files',Buffer.alloc(Number(fileBytes),65),'application/octet-stream',fileName||'fixture.bin');
        const sourceFiles=[];
        if(fixtureFiles!==undefined) {
            if(!Array.isArray(fixtureFiles)||fixtureFiles.length>4||fileBytes!==undefined)
                throw new Error('Use only predefined synthetic files');
            for(const name of fixtureFiles) {
                let bytes,type;
                if(name==='fixture.txt') {
                    bytes=Buffer.from('Fixture text UTF-8: Ti\u1ebfng Vi\u1ec7t\n','utf8');type='text/plain';
                } else if(name==='fixture.png') {
                    bytes=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aR8YAAAAASUVORK5CYII=','base64');type='image/png';
                } else if(name==='fixture.pdf') {
                    const stream='BT /F1 12 Tf 12 30 Td (Fixture PDF) Tj ET\n';
                    const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
                        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 160 80] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
                        '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
                        '<< /Length '+Buffer.byteLength(stream)+' >>\nstream\n'+stream+'endstream'];
                    let pdf='%PDF-1.4\n';const offsets=[0];
                    objects.forEach((obj,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=(i+1)+' 0 obj\n'+obj+'\nendobj\n';});
                    const xref=Buffer.byteLength(pdf);
                    pdf+='xref\n0 6\n0000000000 65535 f \n'+offsets.slice(1).map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('')
                        +'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n'+xref+'\n%%EOF\n';
                    bytes=Buffer.from(pdf,'ascii');type='application/pdf';
                } else throw new Error('Unknown predefined fixture file');
                part(kind==='modern'?'attachments':'files',bytes,type,name);
                sourceFiles.push({filename:name,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});
            }
        }
        parts.push(Buffer.from('--'+boundary+'--\r\n'));
        const relative=expand(url);
        const r=await request(actor,'POST',relative,Buffer.concat(parts),{'Content-Type':'multipart/form-data; boundary='+boundary});
        if(sourceFiles.length) r.sourceFiles=sourceFiles;
        record(task,actor,'POST',relative,r);return;
    }
    throw new Error('Expected seed, login, request, callback, tls-check, tls-info, mail-fault, oauth-fault or multipart.');
}
main().catch(e=>{console.error('Manual fixture command failed: '+redact(e.message));process.exitCode=1});
