// ============================================================
// backend-proxy/worker.js
// ------------------------------------------------------------
// Proxy endurecido: a ANTHROPIC_API_KEY vive SOMENTE aqui (nunca no
// APK — imune a descompilação). Autenticação por HMAC-SHA256
// sobre timestamp+nonce+corpo, não um shared secret simples.
//
// Deploy: `wrangler deploy` numa conta Cloudflare gratuita.
// Precisa de 3 coisas configuradas antes:
//   1. wrangler secret put ANTHROPIC_API_KEY
//   2. wrangler secret put PROXY_SHARED_SECRET   (mesmo valor do
//      PROXY_SHARED_SECRET no local.properties do app)
//   3. habilitar o Durable Object AUTH_GUARD conforme wrangler.toml
//
// Rotação sem downtime: rode `provision.py --rotate`, ele gera
// PROXY_SHARED_SECRET_NEXT. Configure-o também via
// `wrangler secret put PROXY_SHARED_SECRET_NEXT` — o worker aceita
// o secret atual OU o novo durante a transição. Depois que o app
// atualizado estiver em uso, promova o NEXT a atual e remova o
// antigo (ver runbook impresso pelo provision.py).
// ============================================================

const FRESH_WINDOW = 300; // ±5 min de tolerância de relógio
// Limite do corpo (screenshots em base64 ficam perto de 1–3 MB). Acima disso, rejeita
// antes de gastar CPU/HMAC ou cota do provedor.
const MAX_BODY_BYTES = 6 * 1024 * 1024;
const encoder = new TextEncoder();

async function hmacHex(secret, msg) {
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(msg));
  return [...new Uint8Array(sig)].map(b => b.toString(16).padStart(2, "0")).join("");
}

// Comparação em tempo constante — evita vazar o segredo por timing attack.
function secureEqual(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return json({ error: "method not allowed" }, 405);
    }

    // 1. Headers obrigatórios de autenticação.
    const ts = request.headers.get("x-timestamp");
    const nonce = request.headers.get("x-nonce");
    const sig = request.headers.get("x-signature");
    const clientId = request.headers.get("x-client-id");
    if (!ts || !nonce || !sig || !clientId || !/^[A-Za-z0-9._:-]{1,128}$/.test(clientId)) {
      return json({ error: "missing auth headers" }, 401);
    }

    // 2. Janela de freshness — corta timestamps velhos ou "do futuro".
    const skew = Math.abs(Date.now() / 1000 - Number(ts));
    if (!Number.isFinite(Number(ts)) || skew > FRESH_WINDOW) {
      return json({ error: "timestamp outside window" }, 401);
    }

    // 3. Verifica a assinatura HMAC ANTES de qualquer outra coisa —
    //    tudo que vem depois disso só roda pra requisições autênticas.
    //    Aceita o secret atual OU o "NEXT" (janela de rotação).
    const declared = Number(request.headers.get("content-length") || 0);
    if (declared > MAX_BODY_BYTES) {
      return json({ error: "payload too large" }, 413);
    }
    const body = await request.text();
    if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) {
      return json({ error: "payload too large" }, 413);
    }
    const signedMessage = `${ts}.${nonce}.${clientId}.${body}`;
    if (!env.PROXY_SHARED_SECRET) return json({ error: "proxy authentication is not configured" }, 503);
    const expected = await hmacHex(env.PROXY_SHARED_SECRET, signedMessage);
    let valid = secureEqual(sig, expected);
    if (!valid && env.PROXY_SHARED_SECRET_NEXT) {
      const alt = await hmacHex(env.PROXY_SHARED_SECRET_NEXT, signedMessage);
      valid = secureEqual(sig, alt);
    }
    if (!valid) {
      return json({ error: "invalid signature" }, 401);
    }

    // Nonce e limite são verificados em uma transação serializada por cliente.
    if (!env.AUTH_GUARD) return json({ error: "authentication guard is not configured" }, 503);
    const guard = env.AUTH_GUARD.get(env.AUTH_GUARD.idFromName(clientId));
    const guarded = await guard.fetch("https://auth-guard/check", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ nonce, now: Date.now() }),
    });
    if (!guarded.ok) {
      const result = await guarded.json().catch(() => ({}));
      return json({ error: result.error || "request rejected" }, guarded.status);
    }
    const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
    const ipGuard = env.AUTH_GUARD.get(env.AUTH_GUARD.idFromName(`ip:${clientIp}`));
    const ipLimited = await ipGuard.fetch("https://auth-guard/ip-rate", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ mode: "ip-rate", now: Date.now() }),
    });
    if (!ipLimited.ok) {
      const result = await ipLimited.json().catch(() => ({}));
      return json({ error: result.error || "IP rate limited" }, ipLimited.status);
    }

    // 6v. Pedidos de visão (screenshot): Gemini (grátis) ou, se houver key Anthropic, passthrough.
    let parsed = null;
    try { parsed = JSON.parse(body); } catch (_) {}
    if (parsed && parsed._probe === true) return json({ ok: true });
    if (parsed && parsed.vision) {
      delete parsed.vision;
      const plain = !!parsed.plain; delete parsed.plain;
      if (env.GEMINI_API_KEY) return runGemini(env, parsed, plain);
      if (!env.ANTHROPIC_API_KEY) return json({ error: "vision backend not configured (GEMINI_API_KEY)" }, 501);
      return passToAnthropic(env, JSON.stringify(parsed));
    }

    // 6a. Modo gratuito: sem ANTHROPIC_API_KEY, usa Cloudflare Workers AI
    //     (binding `AI`, 10.000 neurons/dia grátis). O app continua a falar o
    //     formato Anthropic: traduzimos pedido e resposta aqui.
    if (!env.ANTHROPIC_API_KEY && env.AI) {
      return runWorkersAI(env, body);
    }

    // 6. Encaminha pra Anthropic — a key NUNCA sai do Worker.
    return passToAnthropic(env, body);
  },
};

export class AuthGuard {
  constructor(ctx) {
    this.ctx = ctx;
  }

  async fetch(request) {
    const payload = await request.json();
    const { nonce, now } = payload;
    if (payload.mode === "ip-rate") {
      let ipResult = { status: 200, error: null };
      await this.ctx.storage.transaction(async (txn) => {
        const bucket = Math.floor(now / 60_000);
        const previous = await txn.get("ip-rate");
        const count = previous?.bucket === bucket ? previous.count : 0;
        if (count >= 300) {
          ipResult = { status: 429, error: "IP rate limited" };
          return;
        }
        await txn.put("ip-rate", { bucket, count: count + 1 });
      });
      return ipResult.status === 200
        ? json({ ok: true })
        : json({ error: ipResult.error }, ipResult.status);
    }
    if (typeof nonce !== "string" || !/^[a-f0-9]{32,128}$/i.test(nonce)) {
      return json({ error: "invalid nonce" }, 400);
    }
    let result = { status: 200, error: null };
    await this.ctx.storage.transaction(async (txn) => {
      const nonceKey = `n:${nonce}`;
      if (await txn.get(nonceKey)) {
        result = { status: 401, error: "replay detected" };
        return;
      }
      await txn.put(nonceKey, now);
      const bucket = Math.floor(now / 60_000);
      const previous = await txn.get("rate");
      const count = previous?.bucket === bucket ? previous.count : 0;
      if (count >= 30) {
        result = { status: 429, error: "rate limited" };
        return;
      }
      await txn.put("rate", { bucket, count: count + 1 });
    });
    if (result.status === 200) await this.ctx.storage.setAlarm(now + (FRESH_WINDOW + 60) * 1000);
    return result.status === 200 ? json({ ok: true }) : json({ error: result.error }, result.status);
  }

  async alarm() {
    const expiry = Date.now() - (FRESH_WINDOW + 60) * 1000;
    await this.ctx.storage.transaction(async (txn) => {
      const entries = await txn.list({ prefix: "n:", limit: 512 });
      for (const [key, timestamp] of entries) {
        if (timestamp < expiry) await txn.delete(key);
      }
    });
  }
}

async function passToAnthropic(env, body) {
  const upstream = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body,
  });
  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "content-type": "application/json" },
  });
}

// Visão grátis: formato Anthropic (image base64 + text) -> Gemini generateContent.
async function runGemini(env, req, plain) {
  try {
    const parts = [];
    for (const m of req.messages || []) {
      const blocks = Array.isArray(m.content) ? m.content : [{ type: "text", text: String(m.content) }];
      for (const b of blocks) {
        if (b.type === "text") parts.push({ text: b.text });
        if (b.type === "image") parts.push({ inline_data: { mime_type: b.source.media_type, data: b.source.data } });
      }
    }
    const model = env.GEMINI_MODEL || "gemini-2.5-flash";
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify({
        contents: [{ role: "user", parts }],
        generationConfig: plain ? { maxOutputTokens: req.max_tokens || 512 } : { responseMimeType: "application/json", maxOutputTokens: req.max_tokens || 256 },
      }),
    });
    if (!r.ok) return json({ error: "gemini failed", status: r.status }, 502);
    const out = await r.json();
    const text = (out?.candidates?.[0]?.content?.parts || []).map(p => p.text || "").join("");
    return json({ content: [{ type: "text", text }], model, stop_reason: "end_turn" });
  } catch (e) {
    return json({ error: "gemini failed", detail: String(e) }, 502);
  }
}

async function runWorkersAI(env, body) {
  try {
    const req = JSON.parse(body);
    const flat = (c) => Array.isArray(c) ? c.map(b => b.text || "").join("") : String(c ?? "");
    const messages = [];
    if (req.system) messages.push({ role: "system", content: flat(req.system) });
    for (const m of req.messages || []) messages.push({ role: m.role, content: flat(m.content) });

    const model = env.AI_MODEL || "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
    const out = await env.AI.run(model, { messages, max_tokens: req.max_tokens || 1024 });

    let text = out?.response ?? out?.choices?.[0]?.message?.content ?? "";
    if (typeof text !== "string") text = JSON.stringify(text);
    return json({ content: [{ type: "text", text }], model, stop_reason: "end_turn" });
  } catch (e) {
    return json({ error: "workers-ai failed", detail: String(e) }, 502);
  }
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json" },
  });
}
