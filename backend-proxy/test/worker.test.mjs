// Testes do Worker (backend-proxy/worker.js) sem dependências externas.
// Rodar: cd backend-proxy && npm test
import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import worker, { AuthGuard } from "../worker.js";

const SECRET = "test-secret-with-enough-entropy-1234567890";
const CLIENT = "giselle-device-1";
const enc = new TextEncoder();

// ---------- helpers ----------

// Storage em memória que imita o subconjunto de DurableObjectStorage usado pelo AuthGuard.
function fakeStorage() {
  const map = new Map();
  const alarms = [];
  const api = {
    async get(k) { return map.has(k) ? structuredClone(map.get(k)) : undefined; },
    async put(k, v) { map.set(k, structuredClone(v)); },
    async delete(k) { return map.delete(k); },
    async list({ prefix = "", limit = 1000 } = {}) {
      const out = new Map();
      for (const [k, v] of map) {
        if (k.startsWith(prefix)) out.set(k, v);
        if (out.size >= limit) break;
      }
      return out;
    },
    async transaction(fn) { return fn(api); },
    async setAlarm(t) { alarms.push(t); },
  };
  api.alarms = alarms;
  return api;
}

function makeEnv(overrides = {}) {
  const storage = fakeStorage();
  const guard = new AuthGuard({ storage });
  const stubs = new Map();
  return {
    PROXY_SHARED_SECRET: SECRET,
    AUTH_GUARD: {
      idFromName: (name) => name,
      get(id) {
        if (!stubs.has(id)) {
          stubs.set(id, { fetch: (url, init) => guard.fetch(new Request(url, init)) });
        }
        return stubs.get(id);
      },
    },
    ...overrides,
  };
}

async function hmacHex(secret, msg) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(msg));
  return [...new Uint8Array(sig)].map(b => b.toString(16).padStart(2, "0")).join("");
}

let nonceCounter = 0;
function nonce() {
  nonceCounter += 1;
  return nonceCounter.toString(16).padStart(32, "0");
}

async function signedRequest(body, { secret = SECRET, ts = Math.floor(Date.now() / 1000), nonceValue = nonce(), client = CLIENT, headers = {} } = {}) {
  const sig = await hmacHex(secret, `${ts}.${nonceValue}.${client}.${body}`);
  return new Request("https://proxy.example/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-timestamp": String(ts),
      "x-nonce": nonceValue,
      "x-signature": sig,
      "x-client-id": client,
      ...headers,
    },
    body,
  });
}

const CLAUDE_BODY = JSON.stringify({ model: "claude", max_tokens: 64, messages: [{ role: "user", content: "oi" }] });

// Captura chamadas ao fetch global (upstream Anthropic / Gemini).
let upstreamCalls;
const realFetch = globalThis.fetch;
beforeEach(() => {
  upstreamCalls = [];
  globalThis.fetch = async (url, init) => {
    upstreamCalls.push({ url: String(url), init });
    return new Response(JSON.stringify({ id: "msg_1", ok: true }), { status: 200, headers: { "content-type": "application/json" } });
  };
});
afterEach(() => { globalThis.fetch = realFetch; });

// ---------- testes ----------

test("rejeita métodos que não são POST", async () => {
  const res = await worker.fetch(new Request("https://p/x", { method: "GET" }), makeEnv());
  assert.equal(res.status, 405);
});

test("rejeita pedidos sem headers de autenticação", async () => {
  const res = await worker.fetch(new Request("https://p/x", { method: "POST", body: "{}" }), makeEnv());
  assert.equal(res.status, 401);
});

test("rejeita client-id com caracteres inválidos", async () => {
  const req = await signedRequest(CLAUDE_BODY, { client: "bad id!" });
  const res = await worker.fetch(req, makeEnv());
  assert.equal(res.status, 401);
});

test("rejeita timestamp fora da janela de ±5 min", async () => {
  const old = Math.floor(Date.now() / 1000) - 3600;
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY, { ts: old }), makeEnv());
  assert.equal(res.status, 401);
  assert.equal((await res.json()).error, "timestamp outside window");
});

test("rejeita assinatura feita com outro segredo", async () => {
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY, { secret: "outro-segredo" }), makeEnv());
  assert.equal(res.status, 401);
  assert.equal((await res.json()).error, "invalid signature");
});

test("rejeita corpo adulterado após assinatura", async () => {
  const req = await signedRequest(CLAUDE_BODY);
  const tampered = new Request(req.url, { method: "POST", headers: req.headers, body: CLAUDE_BODY.replace("oi", "hack") });
  const res = await worker.fetch(tampered, makeEnv());
  assert.equal(res.status, 401);
});

test("aceita pedido válido e encaminha ao upstream com a chave do provedor", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "sk-ant-test-key-xxxxxxxxxxxxxxxxxxxx" });
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY), env);
  assert.equal(res.status, 200);
  assert.equal(upstreamCalls.length, 1);
  assert.equal(upstreamCalls[0].url, "https://api.anthropic.com/v1/messages");
  assert.equal(upstreamCalls[0].init.headers["x-api-key"], "sk-ant-test-key-xxxxxxxxxxxxxxxxxxxx");
});

test("bloqueia replay do mesmo nonce", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "k" });
  const fixed = nonce();
  const ok = await worker.fetch(await signedRequest(CLAUDE_BODY, { nonceValue: fixed }), env);
  assert.equal(ok.status, 200);
  const replay = await worker.fetch(await signedRequest(CLAUDE_BODY, { nonceValue: fixed }), env);
  assert.equal(replay.status, 401);
  assert.equal((await replay.json()).error, "replay detected");
});

test("rejeita nonce mal formado", async () => {
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY, { nonceValue: "xyz" }), makeEnv({ ANTHROPIC_API_KEY: "k" }));
  assert.equal(res.status, 400);
});

test("aplica limite de 30 pedidos por minuto por cliente", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "k" });
  let last;
  for (let i = 0; i < 31; i++) {
    last = await worker.fetch(await signedRequest(CLAUDE_BODY), env);
  }
  assert.equal(last.status, 429);
});

test("rejeita corpo com Content-Length acima do limite (413) sem chamar upstream", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "k" });
  const big = "x".repeat(10);
  const req = await signedRequest(big, { headers: { "content-length": String(7 * 1024 * 1024) } });
  const res = await worker.fetch(req, env);
  assert.equal(res.status, 413);
  assert.equal(upstreamCalls.length, 0);
});

test("rejeita corpo real acima do limite mesmo sem Content-Length honesto", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "k" });
  const body = JSON.stringify({ blob: "a".repeat(6 * 1024 * 1024 + 10) });
  const res = await worker.fetch(await signedRequest(body), env);
  assert.equal(res.status, 413);
  assert.equal(upstreamCalls.length, 0);
});

test("responde 503 quando o segredo do proxy não está configurado", async () => {
  const env = makeEnv({ PROXY_SHARED_SECRET: undefined, ANTHROPIC_API_KEY: "k" });
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY), env);
  assert.equal(res.status, 503);
});

test("aceita o segredo NEXT durante a rotação", async () => {
  const NEXT = "next-secret-with-enough-entropy-0987654321";
  const env = makeEnv({ PROXY_SHARED_SECRET_NEXT: NEXT, ANTHROPIC_API_KEY: "k" });
  const res = await worker.fetch(await signedRequest(CLAUDE_BODY, { secret: NEXT }), env);
  assert.equal(res.status, 200);
});

test("probe autenticado responde ok sem consumir upstream", async () => {
  const env = makeEnv({ ANTHROPIC_API_KEY: "k" });
  const res = await worker.fetch(await signedRequest(JSON.stringify({ _probe: true })), env);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(upstreamCalls.length, 0);
});

test("sem ANTHROPIC_API_KEY usa Workers AI e traduz a resposta para o formato Anthropic", async () => {
  const calls = [];
  const env = makeEnv({ AI: { run: async (model, input) => { calls.push({ model, input }); return { response: "resposta" }; } } });
  const body = JSON.stringify({ system: "sys", max_tokens: 10, messages: [{ role: "user", content: [{ type: "text", text: "oi" }] }] });
  const res = await worker.fetch(await signedRequest(body), env);
  assert.equal(res.status, 200);
  const out = await res.json();
  assert.equal(out.content[0].text, "resposta");
  assert.equal(calls[0].input.messages[0].role, "system");
  assert.equal(calls[0].input.messages[1].content, "oi");
  assert.equal(upstreamCalls.length, 0);
});

test("visão sem GEMINI_API_KEY nem ANTHROPIC_API_KEY responde 501", async () => {
  const body = JSON.stringify({ vision: true, messages: [] });
  const res = await worker.fetch(await signedRequest(body), makeEnv());
  assert.equal(res.status, 501);
});
