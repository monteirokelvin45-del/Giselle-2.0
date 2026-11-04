// Ferramentas auxiliares do Worker: embeddings (memória semântica) e busca na web com fontes.
// Tudo passa pela mesma autenticação HMAC de worker.js. As chaves vêm só de `env`.
// Nunca devolve o corpo bruto do provedor: só os campos que o app usa.

export const EMBED_MAX_CHARS = 2000;
export const SEARCH_MAX_CHARS = 300;
export const SEARCH_MAX_RESULTS = 5;

export const DEFAULTS_TOOLS = {
  // Multilíngue (português). Confirme o nome com `npx wrangler ai models list`.
  embedModel: "@cf/baai/bge-m3",
  braveUrl: "https://api.search.brave.com/res/v1/web/search",
  tavilyUrl: "https://api.tavily.com/search",
};

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

/** Texto aparado e limitado. Retorna null se vazio ou não for string. */
export function cleanText(value, maxChars) {
  if (typeof value !== "string") return null;
  const t = value.replace(/\s+/g, " ").trim();
  if (!t) return null;
  return t.slice(0, maxChars);
}

/**
 * op "embed": { op, text } -> { embedding: number[], dims, model }
 * Usa o binding AI do Workers (gratuito dentro da cota diária).
 */
export async function runEmbed(env, req) {
  if (!env.AI) return json({ error: "embeddings not configured" }, 503);
  const text = cleanText(req.text, EMBED_MAX_CHARS);
  if (!text) return json({ error: "text is required" }, 400);
  const model = env.EMBED_MODEL || DEFAULTS_TOOLS.embedModel;
  let out;
  try {
    out = await env.AI.run(model, { text: [text] });
  } catch (_) {
    return json({ error: "embedding provider failed" }, 502);
  }
  const vector = out?.data?.[0];
  if (!Array.isArray(vector) || vector.length === 0 || !vector.every(Number.isFinite)) {
    return json({ error: "embedding provider returned no vector" }, 502);
  }
  return json({ embedding: vector, dims: vector.length, model });
}

/** Normaliza resultados de Brave ou Tavily para { title, url, snippet }. */
export function normalizeResults(items, snippetKey) {
  const out = [];
  for (const it of Array.isArray(items) ? items : []) {
    const url = typeof it?.url === "string" && /^https?:\/\//.test(it.url) ? it.url : null;
    const title = cleanText(it?.title, 200);
    if (!url || !title) continue;
    out.push({ title, url, snippet: cleanText(it?.[snippetKey], 400) || "" });
    if (out.length >= SEARCH_MAX_RESULTS) break;
  }
  return out;
}

/**
 * op "search": { op, query, max? } -> { provider, results: [{title,url,snippet}] }
 * Ordem: Brave (BRAVE_API_KEY) e, se falhar ou não houver, Tavily (TAVILY_API_KEY).
 */
export async function runSearch(env, req, { fetchImpl = fetch } = {}) {
  const query = cleanText(req.query, SEARCH_MAX_CHARS);
  if (!query) return json({ error: "query is required" }, 400);
  const max = Math.min(Math.max(Number(req.max) || SEARCH_MAX_RESULTS, 1), SEARCH_MAX_RESULTS);
  const tried = [];

  if (env.BRAVE_API_KEY) {
    tried.push("brave");
    const r = await fetchImpl(`${DEFAULTS_TOOLS.braveUrl}?q=${encodeURIComponent(query)}&count=${max}`, {
      headers: { accept: "application/json", "x-subscription-token": env.BRAVE_API_KEY },
    }).catch(() => null);
    if (r && r.ok) {
      const data = await r.json().catch(() => null);
      const results = normalizeResults(data?.web?.results, "description").slice(0, max);
      if (results.length) return json({ provider: "brave", results });
    }
  }

  if (env.TAVILY_API_KEY) {
    tried.push("tavily");
    const r = await fetchImpl(DEFAULTS_TOOLS.tavilyUrl, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${env.TAVILY_API_KEY}` },
      body: JSON.stringify({ query, max_results: max, include_answer: false }),
    }).catch(() => null);
    if (r && r.ok) {
      const data = await r.json().catch(() => null);
      const results = normalizeResults(data?.results, "content").slice(0, max);
      if (results.length) return json({ provider: "tavily", results });
    }
  }

  if (tried.length === 0) return json({ error: "search not configured (BRAVE_API_KEY or TAVILY_API_KEY)" }, 503);
  return json({ error: "search providers failed", tried }, 502);
}

// ---------- Memória vetorial no servidor (Cloudflare Vectorize) ----------
// Opt-in: só funciona se o binding MEMORY_INDEX existir (ver wrangler.toml).
// Cada cliente tem o seu namespace, para que um aparelho não leia as memórias de outro.
// Atenção: o texto da memória (content) fica guardado no Cloudflare. O app não envia nada
// por este caminho sem decisão explícita do utilizador.
export const VECTOR_MAX_DIMS = 1536;
export const VECTOR_MAX_TOPK = 10;

export function cleanVectorId(v) {
  return typeof v === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(v) ? v : null;
}

export function cleanVector(v) {
  if (!Array.isArray(v) || v.length === 0 || v.length > VECTOR_MAX_DIMS) return null;
  return v.every(Number.isFinite) ? v : null;
}

export async function runVectorUpsert(env, req, clientId) {
  if (!env.MEMORY_INDEX) return json({ error: "vector index not configured" }, 503);
  const id = cleanVectorId(req.id);
  const values = cleanVector(req.values);
  if (!id || !values) return json({ error: "id and values are required" }, 400);
  const content = cleanText(req.content, 500) || "";
  const category = cleanText(req.category, 32) || "fact";
  try {
    await env.MEMORY_INDEX.upsert([{ id, values, namespace: clientId, metadata: { category, content } }]);
  } catch (_) {
    return json({ error: "vector index failed" }, 502);
  }
  return json({ ok: true, id, dims: values.length });
}

export async function runVectorQuery(env, req, clientId) {
  if (!env.MEMORY_INDEX) return json({ error: "vector index not configured" }, 503);
  const values = cleanVector(req.values);
  if (!values) return json({ error: "values are required" }, 400);
  const topK = Math.min(Math.max(Number(req.topK) || 5, 1), VECTOR_MAX_TOPK);
  let res;
  try {
    res = await env.MEMORY_INDEX.query(values, { topK, namespace: clientId, returnMetadata: "all" });
  } catch (_) {
    return json({ error: "vector index failed" }, 502);
  }
  const matches = (res?.matches || []).map((m) => ({
    id: m.id,
    score: Number.isFinite(m.score) ? m.score : 0,
    category: cleanText(m.metadata?.category, 32) || "fact",
    content: cleanText(m.metadata?.content, 500) || "",
  }));
  return json({ matches });
}
