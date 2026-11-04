// Testes da cadeia de provedores (backend-proxy/providers.js). Sem rede: fetch simulado.
import { test } from "node:test";
import assert from "node:assert/strict";
import { anthropicToOpenAI, openAIToAnthropic, runChat, configuredProviders, runNimVision } from "../providers.js";

const BODY = JSON.stringify({
  model: "claude-sonnet-4-6",
  max_tokens: 100,
  system: [{ type: "text", text: "sistema" }],
  messages: [{ role: "user", content: "oi" }],
});

// fetch simulado: responde por host, registra chamadas.
function fakeFetch(routes) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    const r = routes[url];
    if (!r) throw new Error("rede indisponível: " + url);
    if (r.throw) throw new Error(r.throw);
    return new Response(JSON.stringify(r.json ?? {}), { status: r.status ?? 200, headers: { "content-type": "application/json" } });
  };
  fn.calls = calls;
  return fn;
}

const GROQ = "https://api.groq.com/openai/v1/chat/completions";
const NIM = "https://integrate.api.nvidia.com/v1/chat/completions";
const ANTH = "https://api.anthropic.com/v1/messages";
const okOpenAI = (text, model = "m") => ({ choices: [{ message: { content: text } }], model });

test("traduz system, texto e imagem do formato Anthropic para OpenAI", () => {
  const out = anthropicToOpenAI({
    system: [{ type: "text", text: "S" }],
    messages: [{ role: "user", content: [
      { type: "image", source: { type: "base64", media_type: "image/jpeg", data: "AAA" } },
      { type: "text", text: "o que é isso?" },
    ] }, { role: "assistant", content: "ok" }],
  }, "modelo-x");
  assert.equal(out.model, "modelo-x");
  assert.deepEqual(out.messages[0], { role: "system", content: "S" });
  assert.equal(out.messages[1].content[0].image_url.url, "data:image/jpeg;base64,AAA");
  assert.equal(out.messages[1].content[1].text, "o que é isso?");
  assert.equal(out.messages[2].content, "ok");
});

test("mensagem só de texto vira string simples", () => {
  const out = anthropicToOpenAI({ messages: [{ role: "user", content: [{ type: "text", text: "a" }, { type: "text", text: "b" }] }] }, "m");
  assert.equal(out.messages[0].content, "ab");
});

test("resposta OpenAI volta no formato Anthropic", () => {
  const out = openAIToAnthropic(okOpenAI("{\"x\":1}", "llama"), "fallback");
  assert.equal(out.content[0].text, "{\"x\":1}");
  assert.equal(out.model, "llama");
  assert.equal(out.stop_reason, "end_turn");
});

test("configuredProviders segue a ordem Groq -> Anthropic -> NIM -> Workers AI", () => {
  assert.deepEqual(configuredProviders({}), []);
  assert.deepEqual(configuredProviders({ GROQ_API_KEY: "g", NVIDIA_API_KEY: "n", AI: {} }), ["groq", "nvidia", "workers-ai"]);
  assert.deepEqual(configuredProviders({ ANTHROPIC_API_KEY: "a", GROQ_API_KEY: "g" }), ["groq", "anthropic"]);
});

test("com Groq configurado, usa Groq com a chave e o modelo padrão", async () => {
  const f = fakeFetch({ [GROQ]: { json: okOpenAI("pronto") } });
  const r = await runChat({ GROQ_API_KEY: "gsk_test" }, BODY, { fetchImpl: f });
  assert.equal(r.ok, true);
  assert.equal(r.provider, "groq");
  assert.equal(f.calls.length, 1);
  assert.equal(f.calls[0].init.headers.authorization, "Bearer gsk_test");
  assert.equal(JSON.parse(f.calls[0].init.body).model, "llama-3.3-70b-versatile");
  const body = await (await r.response).json();
  assert.equal(body.content[0].text, "pronto");
});

test("Groq com 429 cai para NVIDIA NIM", async () => {
  const f = fakeFetch({ [GROQ]: { status: 429, json: {} }, [NIM]: { json: okOpenAI("via nim") } });
  const r = await runChat({ GROQ_API_KEY: "g", NVIDIA_API_KEY: "n" }, BODY, { fetchImpl: f });
  assert.equal(r.ok, true);
  assert.equal(r.provider, "nvidia");
  assert.equal(f.calls.length, 2);
  assert.equal(JSON.parse(f.calls[1].init.body).model, "meta/llama-3.3-70b-instruct");
});

test("erro de rede em Groq também cai para o próximo", async () => {
  const f = fakeFetch({ [GROQ]: { throw: "reset" }, [NIM]: { json: okOpenAI("ok") } });
  const r = await runChat({ GROQ_API_KEY: "g", NVIDIA_API_KEY: "n" }, BODY, { fetchImpl: f });
  assert.equal(r.provider, "nvidia");
});

test("Groq é a primeira escolha; Anthropic só entra como reserva", async () => {
  const f = fakeFetch({ [ANTH]: { json: { content: [{ type: "text", text: "claude" }] } }, [GROQ]: { json: okOpenAI("x") } });
  const r = await runChat({ ANTHROPIC_API_KEY: "sk-ant", GROQ_API_KEY: "g" }, BODY, { fetchImpl: f });
  assert.equal(r.provider, "groq");
  assert.equal(f.calls.length, 1);
});

test("Groq fora do ar cai para Anthropic", async () => {
  const f = fakeFetch({ [GROQ]: { status: 503 }, [ANTH]: { json: { content: [{ type: "text", text: "claude" }] } } });
  const r = await runChat({ ANTHROPIC_API_KEY: "sk-ant", GROQ_API_KEY: "g" }, BODY, { fetchImpl: f });
  assert.equal(r.provider, "anthropic");
  assert.equal(f.calls[1].init.headers["x-api-key"], "sk-ant");
});

test("chave inválida (401) também passa para o próximo provedor", async () => {
  const f = fakeFetch({ [GROQ]: { status: 401, json: {} }, [NIM]: { json: okOpenAI("ok") } });
  const r = await runChat({ GROQ_API_KEY: "bad", NVIDIA_API_KEY: "n" }, BODY, { fetchImpl: f });
  assert.equal(r.provider, "nvidia");
});

test("erro 400 do pedido não é repetido em outro provedor", async () => {
  const f = fakeFetch({ [GROQ]: { status: 400, json: {} }, [NIM]: { json: okOpenAI("ok") } });
  const r = await runChat({ GROQ_API_KEY: "g", NVIDIA_API_KEY: "n" }, BODY, { fetchImpl: f });
  assert.equal(r.ok, false);
  assert.equal(r.status, 400);
  assert.equal(f.calls.length, 1);
});

test("todos falham -> ok false com lista de tentativas, sem chaves", async () => {
  const f = fakeFetch({ [GROQ]: { status: 503 }, [NIM]: { throw: "timeout" } });
  const r = await runChat({ GROQ_API_KEY: "SECRET-G", NVIDIA_API_KEY: "SECRET-N" }, BODY, { fetchImpl: f });
  assert.equal(r.ok, false);
  assert.equal(r.status, 502);
  assert.equal(r.tried.length, 2);
  assert.ok(!JSON.stringify(r.tried).includes("SECRET"));
});

test("visão via NVIDIA NIM usa o modelo de visão e devolve texto", async () => {
  const f = fakeFetch({ [NIM]: { json: okOpenAI("uma foto") } });
  const res = await runNimVision({ NVIDIA_API_KEY: "n" }, { messages: [{ role: "user", content: [{ type: "image", source: { type: "base64", media_type: "image/jpeg", data: "A" } }, { type: "text", text: "?" }] }] }, { fetchImpl: f });
  assert.equal(JSON.parse(f.calls[0].init.body).model, "meta/llama-3.2-11b-vision-instruct");
  assert.equal((await res.json()).content[0].text, "uma foto");
});

test("visão sem NVIDIA_API_KEY devolve null", async () => {
  assert.equal(await runNimVision({}, { messages: [] }, { fetchImpl: fakeFetch({}) }), null);
});
