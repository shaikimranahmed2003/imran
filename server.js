'use strict';
/**
 * TGNPDCL Employee API - zero dependencies (Node >= 18).
 *
 * The HR payroll web app PUBLISHES payslips here (admin key).
 * The Android app LOGS IN here (employee ID + password) and reads only that employee's payslips.
 *
 * Environment:
 *   ADMIN_KEY         (required, >= 12 chars) secret used by the payroll web app to publish
 *   PORT              default 3000
 *   DATA_DIR          default ./data  (must be on persistent storage)
 *   DEFAULT_PASSWORD  default "npdcl" (first-login password; employee must change it)
 *   TOKEN_SECRET      optional; generated and stored in DATA_DIR if not set
 *   TRUST_PROXY       set to 1 when behind a reverse proxy (uses X-Forwarded-For for rate limiting)
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 3000);
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const ADMIN_KEY = process.env.ADMIN_KEY || '';
const DEFAULT_PASSWORD = process.env.DEFAULT_PASSWORD || 'npdcl';
const TRUST_PROXY = process.env.TRUST_PROXY === '1';

const TOKEN_TTL_MS = 30 * 60 * 1000;      // sessions last 30 minutes
const MAX_FAILS = 5;                       // wrong passwords per employee ID ...
const LOCK_MS = 15 * 60 * 1000;            // ... then locked for 15 minutes
const IP_MAX = 40, IP_WINDOW_MS = 10 * 60 * 1000;
const MAX_BODY = 20 * 1024 * 1024;

if (ADMIN_KEY.length < 12) {
  console.error('ERROR: set the ADMIN_KEY environment variable (at least 12 characters).');
  process.exit(1);
}

fs.mkdirSync(DATA_DIR, { recursive: true });
const DB_FILE = path.join(DATA_DIR, 'db.json');
const SECRET_FILE = path.join(DATA_DIR, 'secret.key');

/* ---------- storage ---------- */
let db = { employees: {}, payslips: {} };
if (fs.existsSync(DB_FILE)) db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
function saveDb() {
  const tmp = DB_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(db));
  fs.renameSync(tmp, DB_FILE);
}

let SECRET = process.env.TOKEN_SECRET;
if (!SECRET) {
  if (fs.existsSync(SECRET_FILE)) SECRET = fs.readFileSync(SECRET_FILE, 'utf8').trim();
  else {
    SECRET = crypto.randomBytes(32).toString('hex');
    fs.writeFileSync(SECRET_FILE, SECRET, { mode: 0o600 });
  }
}

/* ---------- crypto helpers ---------- */
const norm = (s) => String(s == null ? '' : s).trim().toUpperCase();
const b64u = (buf) => Buffer.from(buf).toString('base64url');

function hashPassword(pw) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(pw, salt, 64);
  return `s1$${salt.toString('hex')}$${hash.toString('hex')}`;
}
function verifyPassword(pw, stored) {
  const [v, saltHex, hashHex] = String(stored || '').split('$');
  if (v !== 's1' || !saltHex || !hashHex) return false;
  const expected = Buffer.from(hashHex, 'hex');
  const actual = crypto.scryptSync(pw, Buffer.from(saltHex, 'hex'), expected.length);
  return crypto.timingSafeEqual(actual, expected);
}
const DUMMY_HASH = hashPassword('dummy-password-for-timing');

function sign(payload) {
  const body = b64u(JSON.stringify(payload));
  const mac = b64u(crypto.createHmac('sha256', SECRET).update(body).digest());
  return `${body}.${mac}`;
}
function verifyToken(token) {
  const [body, mac] = String(token || '').split('.');
  if (!body || !mac) return null;
  const expected = crypto.createHmac('sha256', SECRET).update(body).digest();
  let given;
  try { given = Buffer.from(mac, 'base64url'); } catch { return null; }
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null;
  let p;
  try { p = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')); } catch { return null; }
  if (!p || typeof p.exp !== 'number' || p.exp < Date.now()) return null;
  return p;
}
function safeEqual(a, b) {
  const x = crypto.createHash('sha256').update(String(a)).digest();
  const y = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(x, y);
}
function issueToken(empNo, emp) {
  return sign({ sub: empNo, pv: emp.pv || 0, mc: !!emp.mustChange, exp: Date.now() + TOKEN_TTL_MS });
}

/* ---------- rate limiting (in memory) ---------- */
const idFails = new Map();   // empNo -> {count, lockedUntil}
const ipHits = new Map();    // ip -> {count, start}
function clientIp(req) {
  if (TRUST_PROXY) {
    const xf = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
    if (xf) return xf;
  }
  return req.socket.remoteAddress || 'unknown';
}
function ipAllowed(ip) {
  const now = Date.now();
  let h = ipHits.get(ip);
  if (!h || now - h.start > IP_WINDOW_MS) { h = { count: 0, start: now }; ipHits.set(ip, h); }
  h.count++;
  return h.count <= IP_MAX;
}
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of ipHits) if (now - v.start > IP_WINDOW_MS) ipHits.delete(k);
  for (const [k, v] of idFails) if (v.lockedUntil && v.lockedUntil < now) idFails.delete(k);
}, 5 * 60 * 1000).unref();

/* ---------- http helpers ---------- */
const BASE_HEADERS = {
  'Content-Type': 'application/json; charset=utf-8',
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff'
};
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, X-Admin-Key',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Max-Age': '600'
};
function send(res, status, obj, cors) {
  res.writeHead(status, cors ? { ...BASE_HEADERS, ...CORS } : BASE_HEADERS);
  res.end(JSON.stringify(obj));
}
function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) { reject({ status: 413, error: 'Request too large' }); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => {
      try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); }
      catch { reject({ status: 400, error: 'Invalid JSON' }); }
    });
    req.on('error', () => reject({ status: 400, error: 'Bad request' }));
  });
}
function authEmployee(req, allowMustChange) {
  const h = String(req.headers.authorization || '');
  const t = h.startsWith('Bearer ') ? h.slice(7) : '';
  const p = verifyToken(t);
  if (!p) return { err: [401, 'Session expired. Please sign in again.', 'session'] };
  const emp = db.employees[p.sub];
  if (!emp || (emp.pv || 0) !== p.pv) return { err: [401, 'Session expired. Please sign in again.', 'session'] };
  if (emp.mustChange && !allowMustChange) return { err: [403, 'Please set a new password first.', 'password_change_required'] };
  return { empNo: p.sub, emp };
}

/* ---------- sanitizers for published data ---------- */
const str = (v, max = 120) => String(v == null ? '' : v).slice(0, max);
const num = (v) => { const n = Number(v); return Number.isFinite(n) ? Math.round(n * 100) / 100 : 0; };
const lines = (arr) => (Array.isArray(arr) ? arr : []).slice(0, 200)
  .map((x) => ({ label: str(x && x.label), amount: num(x && x.amount), prorated: !!(x && x.prorated) }));
function cleanProfile(p) {
  p = p || {};
  return {
    name: str(p.name), designation: str(p.designation), office: str(p.office), station: str(p.station),
    department: str(p.department), doj: str(p.doj, 20), bankName: str(p.bankName),
    bankAccMasked: str(p.bankAccMasked, 40), panMasked: str(p.panMasked, 20), gpfEpf: str(p.gpfEpf, 40)
  };
}
function cleanPayslip(p, month) {
  p = p || {};
  return {
    month, label: str(p.label, 40), lopDays: num(p.lopDays), sanctionedDays: num(p.sanctionedDays),
    daysInMonth: num(p.daysInMonth), earnings: lines(p.earnings), deductions: lines(p.deductions),
    totalEarnings: num(p.totalEarnings), totalDeductions: num(p.totalDeductions), net: num(p.net)
  };
}

/* ---------- routes ---------- */
async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const m = req.method;

  if (m === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
  if (m === 'GET' && p === '/api/health') return send(res, 200, { ok: true }, true);

  /* ----- admin (payroll web app) ----- */
  if (p.startsWith('/api/admin/')) {
    if (!safeEqual(req.headers['x-admin-key'] || '', ADMIN_KEY)) return send(res, 401, { error: 'Invalid admin key' }, true);

    if (m === 'POST' && p === '/api/admin/publish') {
      const body = await readJson(req);
      const month = String(body.month || '');
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return send(res, 400, { error: 'month must be YYYY-MM' }, true);
      if (!Array.isArray(body.employees) || !body.employees.length) return send(res, 400, { error: 'No employees supplied' }, true);
      let published = 0, created = 0, skipped = 0;
      for (const item of body.employees) {
        const empNo = norm(item && item.empNo);
        if (!empNo || empNo.length > 32 || !item.payslip) { skipped++; continue; }
        let emp = db.employees[empNo];
        if (!emp) {
          emp = db.employees[empNo] = { passHash: hashPassword(DEFAULT_PASSWORD), mustChange: true, pv: 0, profile: {} };
          created++;
        }
        emp.profile = cleanProfile(item.profile);
        (db.payslips[empNo] = db.payslips[empNo] || {})[month] = cleanPayslip(item.payslip, month);
        published++;
      }
      saveDb();
      return send(res, 200, { month, published, newEmployees: created, skipped }, true);
    }

    if (m === 'POST' && p === '/api/admin/reset-password') {
      const body = await readJson(req);
      const empNo = norm(body.empNo);
      const emp = db.employees[empNo];
      if (!emp) return send(res, 404, { error: 'Employee not found (publish a payslip for them first)' }, true);
      emp.passHash = hashPassword(DEFAULT_PASSWORD); emp.mustChange = true; emp.pv = (emp.pv || 0) + 1;
      idFails.delete(empNo);
      saveDb();
      return send(res, 200, { ok: true, empNo }, true);
    }
    return send(res, 404, { error: 'Not found' }, true);
  }

  /* ----- employee app ----- */
  if (m === 'POST' && p === '/api/login') {
    if (!ipAllowed(clientIp(req))) return send(res, 429, { error: 'Too many attempts. Try again later.', code: 'rate_limited' });
    const body = await readJson(req);
    const empNo = norm(body.empNo);
    const password = String(body.password || '');
    const f = idFails.get(empNo);
    if (f && f.lockedUntil > Date.now()) {
      return send(res, 429, { error: 'Account locked for 15 minutes after too many wrong passwords. Contact HR to reset.', code: 'locked' });
    }
    const emp = db.employees[empNo];
    const ok = verifyPassword(password, emp ? emp.passHash : DUMMY_HASH) && !!emp;
    if (!ok) {
      if (emp) {
        const cur = idFails.get(empNo) || { count: 0, lockedUntil: 0 };
        cur.count++;
        if (cur.count >= MAX_FAILS) { cur.lockedUntil = Date.now() + LOCK_MS; cur.count = 0; }
        idFails.set(empNo, cur);
      }
      return send(res, 401, { error: 'Invalid employee ID or password', code: 'invalid_credentials' });
    }
    idFails.delete(empNo);
    return send(res, 200, { token: issueToken(empNo, emp), mustChange: !!emp.mustChange, name: emp.profile.name || empNo });
  }

  if (m === 'GET' && p === '/api/me') {
    const a = authEmployee(req, true);
    if (a.err) return send(res, a.err[0], { error: a.err[1], code: a.err[2] });
    return send(res, 200, { empNo: a.empNo, mustChange: !!a.emp.mustChange, profile: { empNo: a.empNo, ...a.emp.profile } });
  }

  if (m === 'POST' && p === '/api/change-password') {
    const a = authEmployee(req, true);
    if (a.err) return send(res, a.err[0], { error: a.err[1], code: a.err[2] });
    const body = await readJson(req);
    const cur = String(body.currentPassword || ''), nw = String(body.newPassword || '');
    if (!verifyPassword(cur, a.emp.passHash)) return send(res, 400, { error: 'Current password is incorrect', code: 'wrong_current' });
    if (nw.length < 8) return send(res, 400, { error: 'New password must be at least 8 characters' });
    if (nw === cur) return send(res, 400, { error: 'New password must be different from the current one' });
    if (nw.toLowerCase() === DEFAULT_PASSWORD.toLowerCase() || norm(nw) === a.empNo)
      return send(res, 400, { error: 'Choose a password that is not the default or your employee ID' });
    a.emp.passHash = hashPassword(nw); a.emp.mustChange = false; a.emp.pv = (a.emp.pv || 0) + 1;
    saveDb();
    return send(res, 200, { token: issueToken(a.empNo, a.emp), mustChange: false });
  }

  if (m === 'GET' && p === '/api/payslips') {
    const a = authEmployee(req, false);
    if (a.err) return send(res, a.err[0], { error: a.err[1], code: a.err[2] });
    const all = db.payslips[a.empNo] || {};
    const list = Object.keys(all).sort().reverse().map((k) => ({
      month: k, label: all[k].label, net: all[k].net, totalEarnings: all[k].totalEarnings, totalDeductions: all[k].totalDeductions
    }));
    return send(res, 200, { payslips: list });
  }

  const mm = p.match(/^\/api\/payslips\/(\d{4}-\d{2})$/);
  if (m === 'GET' && mm) {
    const a = authEmployee(req, false);
    if (a.err) return send(res, a.err[0], { error: a.err[1], code: a.err[2] });
    const slip = (db.payslips[a.empNo] || {})[mm[1]];
    if (!slip) return send(res, 404, { error: 'Payslip not found' });
    return send(res, 200, { payslip: slip, profile: { empNo: a.empNo, ...a.emp.profile } });
  }

  return send(res, 404, { error: 'Not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((e) => {
    if (e && e.status) return send(res, e.status, { error: e.error });
    console.error('Unhandled error:', e && e.message);
    send(res, 500, { error: 'Server error' });
  });
});
server.listen(PORT, () => console.log(`Employee API listening on port ${PORT}`));
