// ============================================================
// backend-proxy/providers.js
// ------------------------------------------------------------
// Cadeia de provedores de IA com reserva automática. O app sempre fala o
// formato da Anthropic (Messages API); aqui traduzimos para o formato
// OpenAI Chat Completions (usado por Groq e NVIDIA NIM) e devolvemos a
// resposta no formato Anthropic. Nenhuma chave sai daqui.
//
// Ordem (cada etapa só entra se a sua chave/binding existir):
//   1. Anthropic  — ANTHROPIC_API_KEY   (melhor qualidade para decisões)
//   2. Groq       — GROQ_API_KEY        (muito rápido; modelo GROQ_MODEL)
//   3. NVIDIA NIM — NVIDIA_API_KEY      (API gratuita; modelo NIM_MODEL)
//   4. Workers AI — binding AI          (grátis, sem chave externa)
// Falhas transitórias (rede, 408/425/429/5xx) e erros de autenticação
// passam para o próximo provedor. Só falha se todos falharem.
// ============================================================

export const DEFAULTS = {
  groqModel: "llama-3.3-70b-versatile",
  nimModel: "meta/llama-3.3-70b-instruct",
  nimVisionModel: "meta/llama-3.2-11b-vision-instruct",
  workersModel: "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
};

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const NIM_URL = "https://integrate.api.nvidia.com/v1/chat/completions";

// Erros em que vale tentar o próximo provedor.
const FAILOVER_STATUS = new Set([401, 403, 408, 425, 429, 500, 502, 503, 504]);

// ---------- tradução de formato ----------

function textOf(content) {
  if (Array.isArray(content)) return content.map(b => (b && b.type === "text" ? b.text : "")).join("");
  return String(content ?? "");
}

// Anthropic Messages -> OpenAI Chat Completions.
export function anthropicToOpenAI(req, model) {
  const messages = [];
  if (req.system) messages.push({ role: "system", content: textOf(req.system) });
  for (const m of req.messages || []) {
    if (!Array.isArray(m.content)) {
      messages.push({ role: m.role, content: String(m.content ?? "") });
      continue;
    }
    const parts = [];
    for (const b of m.content) {
      if (b.type === "text") parts.push({ type: "text", text: b.text });
      if (b.type === "image" && b.source?.type === "base64") {
        parts.push({ type: "image_url", image_url: { url: `data:${b.source.media_type};base64,${b.source.data}` } });
      }
    }
    // Mensagens só de texto ficam como string simples (mais compatível).
    const onlyText = parts.every(p => p.type === "text");
    messages.push({ role: m.role, content: onlyText ? parts.map(p => p.text).join("") : parts });
  }
  return {
    model,
    messages,
    max_tokens: req.max_tokens || 1024,
    temperature: req.temperature ?? 0.2,
  };
}

// OpenAI Chat Completions -> Anthropic Messages (forma que o app espera).
export function openAIToAnthropic(json, model) {
  const text = json?.choices?.[0]?.message?.content ?? "";
  return {
    content: [{ type: "text", text: typeof text === "string" ? text : JSON.stringify(text) }],
    model: json?.model || model,
    stop_reason: "end_turn",
  };
}

// ---------- chamadas ----------

async function callOpenAICompatible({ url, key, model, req, fetchImpl }) {
  const upstream = await fetchImpl(url, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify(anthropicToOpenAI(req, model)),
  });
  if (!upstream.ok) return { ok: false, status: upstream.status, provider: url };
  const out = openAIToAnthropic(await upstream.json(), model);
  return { ok: true, body: out, provider: url };
}

async function callAnthropic({ key, rawBody, fetchImpl }) {
  const upstream = await fetchImpl("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01" },
    body: rawBody,
  });
  if (!upstream.ok) return { ok: false, status: upstream.status, provider: "anthropic" };
  return { ok: true, raw: upstream, provider: "anthropic" };
}

async function callWorkersAI({ env, req }) {
  const flat = c => textOf(c);
  const messages = [];
  if (req.system) messages.push({ role: "system", content: flat(req.system) });
  for (const m of req.messages || []) messages.push({ role: m.role, content: flat(m.content) });
  const model = env.AI_MODEL || DEFAULTS.workersModel;
  const out = await env.AI.run(model, { messages, max_tokens: req.max_tokens || 1024 });
  let text = out?.response ?? out?.choices?.[0]?.message?.content ?? "";
  if (typeof text !== "string") text = JSON.stringify(text);
  return { ok: true, body: { content: [{ type: "text", text }], model, stop_reason: "end_turn" }, provider: "workers-ai" };
}

// Provedores configurados, na ordem de preferência.
export function configuredProviders(env) {
  const list = [];
  if (env.ANTHROPIC_API_KEY) list.push("anthropic");
  if (env.GROQ_API_KEY) list.push("groq");
  if (env.NVIDIA_API_KEY) list.push("nvidia");
  if (env.AI) list.push("workers-ai");
  return list;
}

/**
 * Executa o pedido na cadeia. Devolve { ok:true, response: Response } ou
 * { ok:false, status, tried:[...] }. `fetchImpl` é injetável para testes.
 */
export async function runChat(env, rawBody, { fetchImpl = fetch } = {}) {
  let req;
  try { req = JSON.parse(rawBody); } catch (_) { return { ok: false, status: 400, tried: [] }; }
  const tried = [];
  const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

  for (const name of configuredProviders(env)) {
    try {
      let r;
      if (name === "anthropic") r = await callAnthropic({ key: env.ANTHROPIC_API_KEY, rawBody, fetchImpl });
      else if (name === "groq") r = await callOpenAICompatible({ url: GROQ_URL, key: env.GROQ_API_KEY, model: env.GROQ_MODEL || DEFAULTS.groqModel, req, fetchImpl });
      else if (name === "nvidia") r = await callOpenAICompatible({ url: NIM_URL, key: env.NVIDIA_API_KEY, model: env.NIM_MODEL || DEFAULTS.nimModel, req, fetchImpl });
      else r = await callWorkersAI({ env, req });

      if (r.ok) {
        if (r.raw) return { ok: true, response: new Response(r.raw.body, { status: r.raw.status, headers: { "content-type": "application/json" } }), provider: name };
        return { ok: true, response: json(r.body), provider: name };
      }
      tried.push({ provider: name, status: r.status });
      if (!FAILOVER_STATUS.has(r.status)) return { ok: false, status: r.status, tried };
    } catch (e) {
      tried.push({ provider: name, error: String(e && e.message || e) });
    }
  }
  return { ok: false, status: 502, tried };
}

/** Visão via NVIDIA NIM (quando não há Gemini). Mesma tradução de formato. */
export async function runNimVision(env, req, { fetchImpl = fetch } = {}) {
  if (!env.NVIDIA_API_KEY) return null;
  const model = env.NIM_VISION_MODEL || DEFAULTS.nimVisionModel;
  const r = await callOpenAICompatible({ url: NIM_URL, key: env.NVIDIA_API_KEY, model, req, fetchImpl }).catch(() => ({ ok: false, status: 502 }));
  if (!r.ok) return null;
  return new Response(JSON.stringify(r.body), { status: 200, headers: { "content-type": "application/json" } });
}
