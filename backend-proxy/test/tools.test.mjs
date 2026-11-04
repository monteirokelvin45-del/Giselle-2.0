// Testes de tools.js (embeddings e busca). Sem rede: fetch e AI simulados.
// Rodar: cd backend-proxy && npm test
import { test } from "node:test";
import assert from "node:assert/strict";
import { runEmbed, runSearch, normalizeResults, cleanText, runVectorUpsert, runVectorQuery } from "../tools.js";

const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
const fail = (status) => new Response("{}", { status });

test("cleanText limpa espaços e corta no limite", () => {
  assert.equal(cleanText("  a \n b  ", 10), "a b");
  assert.equal(cleanText("abcdef", 3), "abc");
  assert.equal(cleanText("   ", 10), null);
  assert.equal(cleanText(42, 10), null);
});

test("embed: devolve vetor e dimensões do binding AI", async () => {
  const calls = [];
  const env = { AI: { run: async (model, input) => { calls.push({ model, input }); return { data: [[0.1, 0.2, 0.3]] }; } } };
  const res = await runEmbed(env, { text: "  gosto de café  " });
  assert.equal(res.status, 200);
  const out = await res.json();
  assert.deepEqual(out.embedding, [0.1, 0.2, 0.3]);
  assert.equal(out.dims, 3);
  assert.deepEqual(calls[0].input, { text: ["gosto de café"] });
});

test("embed: sem binding AI responde 503", async () => {
  const res = await runEmbed({}, { text: "oi" });
  assert.equal(res.status, 503);
});

test("embed: texto vazio responde 400 e nada é chamado", async () => {
  let called = false;
  const env = { AI: { run: async () => { called = true; return {}; } } };
  const res = await runEmbed(env, { text: "   " });
  assert.equal(res.status, 400);
  assert.equal(called, false);
});

test("embed: vetor inválido ou falha do provedor vira 502 sem vazar detalhes", async () => {
  const bad = { AI: { run: async () => ({ data: [["x"]] }) } };
  assert.equal((await runEmbed(bad, { text: "oi" })).status, 502);
  const boom = { AI: { run: async () => { throw new Error("secret internal detail"); } } };
  const res = await runEmbed(boom, { text: "oi" });
  assert.equal(res.status, 502);
  assert.ok(!(await res.text()).includes("secret internal detail"));
});

test("embed: texto longo é cortado no limite", async () => {
  let sent;
  const env = { AI: { run: async (m, i) => { sent = i.text[0]; return { data: [[1]] }; } } };
  await runEmbed(env, { text: "a".repeat(5000) });
  assert.equal(sent.length, 2000);
});

test("normalizeResults descarta URLs não http e títulos vazios, limita a 5", () => {
  const items = [
    { title: "A", url: "https://a.test", description: "desc a" },
    { title: "", url: "https://b.test" },
    { title: "C", url: "javascript:alert(1)" },
    ...Array.from({ length: 10 }, (_, i) => ({ title: `T${i}`, url: `https://t${i}.test`, description: "d" })),
  ];
  const out = normalizeResults(items, "description");
  assert.equal(out.length, 5);
  assert.equal(out[0].url, "https://a.test");
  assert.ok(out.every((r) => /^https:\/\//.test(r.url)));
});

test("search: sem nenhuma chave responde 503", async () => {
  const res = await runSearch({}, { query: "clima" });
  assert.equal(res.status, 503);
});

test("search: consulta vazia responde 400", async () => {
  const res = await runSearch({ BRAVE_API_KEY: "k" }, { query: "  " });
  assert.equal(res.status, 400);
});

test("search: Brave com fontes, chave só no cabeçalho", async () => {
  const seen = [];
  const fetchImpl = async (url, init) => {
    seen.push({ url, init });
    return ok({ web: { results: [{ title: "Clima", url: "https://meteo.test/x", description: "Chuva amanhã" }] } });
  };
  const res = await runSearch({ BRAVE_API_KEY: "BK" }, { query: "clima maputo", max: 3 }, { fetchImpl });
  const out = await res.json();
  assert.equal(out.provider, "brave");
  assert.equal(out.results[0].url, "https://meteo.test/x");
  assert.equal(out.results[0].snippet, "Chuva amanhã");
  assert.match(seen[0].url, /q=clima%20maputo/);
  assert.match(seen[0].url, /count=3/);
  assert.equal(seen[0].init.headers["x-subscription-token"], "BK");
  assert.ok(!seen[0].url.includes("BK"));
});

test("search: Brave falha -> cai para Tavily", async () => {
  const fetchImpl = async (url) => {
    if (url.includes("brave")) return fail(429);
    return ok({ results: [{ title: "Notícia", url: "https://n.test", content: "resumo" }] });
  };
  const res = await runSearch({ BRAVE_API_KEY: "BK", TAVILY_API_KEY: "TK" }, { query: "x" }, { fetchImpl });
  const out = await res.json();
  assert.equal(out.provider, "tavily");
  assert.equal(out.results[0].snippet, "resumo");
});

test("search: todos falham -> 502 com lista tentada", async () => {
  const fetchImpl = async () => fail(500);
  const res = await runSearch({ BRAVE_API_KEY: "BK", TAVILY_API_KEY: "TK" }, { query: "x" }, { fetchImpl });
  assert.equal(res.status, 502);
  const out = await res.json();
  assert.deepEqual(out.tried, ["brave", "tavily"]);
});

test("search: resposta sem resultados conta como falha", async () => {
  const fetchImpl = async () => ok({ web: { results: [] } });
  const res = await runSearch({ BRAVE_API_KEY: "BK" }, { query: "x" }, { fetchImpl });
  assert.equal(res.status, 502);
});

// ---------- Vectorize ----------
function fakeIndex() {
  const store = [];
  return {
    store,
    async upsert(items) { store.push(...items); },
    async query(values, opts) {
      return { matches: store.filter((v) => v.namespace === opts.namespace).map((v) => ({
        id: v.id, score: 0.9, metadata: v.metadata })) };
    },
  };
}

test("vector: sem índice responde 503", async () => {
  assert.equal((await runVectorUpsert({}, { id: "a", values: [1] }, "c1")).status, 503);
  assert.equal((await runVectorQuery({}, { values: [1] }, "c1")).status, 503);
});

test("vector: id e vetor inválidos viram 400", async () => {
  const env = { MEMORY_INDEX: fakeIndex() };
  assert.equal((await runVectorUpsert(env, { id: "../x", values: [1] }, "c1")).status, 400);
  assert.equal((await runVectorUpsert(env, { id: "ok", values: [] }, "c1")).status, 400);
  assert.equal((await runVectorUpsert(env, { id: "ok", values: ["x"] }, "c1")).status, 400);
  assert.equal(env.MEMORY_INDEX.store.length, 0);
});

test("vector: upsert e query ficam isolados por cliente", async () => {
  const env = { MEMORY_INDEX: fakeIndex() };
  await runVectorUpsert(env, { id: "m1", values: [0.1, 0.2], content: "gosto de café", category: "preference" }, "phone-A");
  await runVectorUpsert(env, { id: "m2", values: [0.3, 0.4], content: "segredo do B" }, "phone-B");
  const res = await runVectorQuery(env, { values: [0.1, 0.2], topK: 5 }, "phone-A");
  const out = await res.json();
  assert.equal(out.matches.length, 1);
  assert.equal(out.matches[0].content, "gosto de café");
  assert.equal(out.matches[0].category, "preference");
});

test("vector: topK limitado a 10 e falha do índice vira 502 sem detalhes", async () => {
  const idx = fakeIndex();
  let seenTopK;
  idx.query = async (v, opts) => { seenTopK = opts.topK; return { matches: [] }; };
  await runVectorQuery({ MEMORY_INDEX: idx }, { values: [1], topK: 999 }, "c");
  assert.equal(seenTopK, 10);
  const boom = { MEMORY_INDEX: { upsert: async () => { throw new Error("internal ids"); } } };
  const res = await runVectorUpsert(boom, { id: "a", values: [1] }, "c");
  assert.equal(res.status, 502);
  assert.ok(!(await res.text()).includes("internal ids"));
});
