// Testes das regras do servidor: npx deno test supabase/functions/ai-gateway/regras_test.ts
import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1";
import {
  custoTextoUsd,
  custoVozUsd,
  erroDoProvedor,
  historiaIdValido,
  imagemBase64,
  lerRespostaClaude,
  LIMITES,
  paraJsonSchema,
  pedidoClaude,
  pedidoFlux,
  SEGURANCA_INFANTIL,
  ssml,
  velocidadeValida,
} from "./regras.ts";

Deno.test("o esquema do Gemini vira JSON Schema do Claude", () => {
  const gemini = {
    type: "OBJECT",
    properties: {
      content: { type: "STRING" },
      choices: {
        type: "ARRAY",
        items: {
          type: "OBJECT",
          properties: { text: { type: "STRING" }, virtue: { type: "STRING", enum: ["coragem", "calma"] } },
          required: ["text", "virtue"],
        },
      },
      isEnding: { type: "BOOLEAN" },
    },
    required: ["content", "choices", "isEnding"],
    propertyOrdering: ["content"],
  };
  assertEquals(paraJsonSchema(gemini), {
    type: "object",
    properties: {
      content: { type: "string" },
      choices: {
        type: "array",
        items: {
          type: "object",
          properties: { text: { type: "string" }, virtue: { type: "string", enum: ["coragem", "calma"] } },
          required: ["text", "virtue"],
          additionalProperties: false,
        },
      },
      isEnding: { type: "boolean" },
    },
    required: ["content", "choices", "isEnding"],
    additionalProperties: false,
  });
});

Deno.test("a proteção infantil do servidor vem antes das instruções do app", () => {
  const corpo = pedidoClaude("claude-sonnet-5", "Você é o Livro Vivo.", "Crie o início.", { type: "object" });
  assert(String(corpo.system).startsWith(SEGURANCA_INFANTIL));
  assert(String(corpo.system).endsWith("Você é o Livro Vivo."));
  assertEquals(corpo.messages, [{ role: "user", content: "Crie o início." }]);
  assertEquals(corpo.output_config, {
    effort: "low",
    format: { type: "json_schema", schema: { type: "object" } },
  });
  // O Sonnet 5 recusa "temperature"; o Haiku 4.5 recusa "effort".
  assert(!("temperature" in corpo));
  assertEquals(pedidoClaude("claude-haiku-4-5", "s", "p", {}).output_config, {
    format: { type: "json_schema", schema: {} },
  });
});

Deno.test("resposta do Claude: JSON, recusa e texto quebrado", () => {
  assertEquals(
    lerRespostaClaude({ stop_reason: "end_turn", content: [{ type: "text", text: '{"content":"Oi"}' }] }),
    { json: { content: "Oi" } },
  );
  assertEquals(lerRespostaClaude({ stop_reason: "refusal", content: [] }), { erro: "BLOQUEADO" });
  assertEquals(
    lerRespostaClaude({ stop_reason: "max_tokens", content: [{ type: "text", text: '{"content":"O' }] }),
    { erro: "RESPOSTA_INVALIDA" },
  );
});

Deno.test("SSML igual ao do app, com o texto escapado", () => {
  assertEquals(
    ssml("pt-BR-LeticiaNeural", "Lia & Bento <3 \"pipoca\" d'água", "-8%"),
    '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="pt-BR">' +
      '<voice name="pt-BR-LeticiaNeural"><prosody rate="-8%">' +
      "Lia &amp; Bento &lt;3 &quot;pipoca&quot; d&apos;água</prosody></voice></speak>",
  );
});

Deno.test("velocidade só entre -30% e +30%", () => {
  for (const ok of ["-8%", "+10%", "0%", "30%"]) assert(velocidadeValida(ok), ok);
  for (const ruim of ["-31%", "fast", "-8", "", 8, "-8%<x>"]) assert(!velocidadeValida(ruim), String(ruim));
});

Deno.test("id de história: o UUID do app passa, texto estranho não", () => {
  assert(historiaIdValido("3f2b8c1e-9a4d-4c6b-8e2f-1a2b3c4d5e6f"));
  for (const ruim of ["", "curto", "a".repeat(65), "../../etc", null, 42]) assert(!historiaIdValido(ruim));
});

Deno.test("custos: voz HD mais cara, texto pelos tokens, modelo desconhecido pelo preço mais alto", () => {
  assertAlmostEquals(custoVozUsd("pt-BR-Macerio:DragonHDLatestNeural", 1_000_000), 22);
  assertAlmostEquals(custoVozUsd("pt-BR-ValerioNeural", 1_000_000), 15);
  assertAlmostEquals(custoTextoUsd("claude-sonnet-5", 3000, 800), 0.014);
  assertAlmostEquals(custoTextoUsd("claude-haiku-4-5-20251001", 3000, 800), 0.007);
  assertAlmostEquals(custoTextoUsd("outro-modelo", 1_000_000, 0), 5);
});

Deno.test("plano grátis: 3 histórias e ilustração só na capa", () => {
  assertEquals(LIMITES.gratis.historias, 3);
  assertEquals(LIMITES.gratis.imagensHistoria, 1);
  assert(LIMITES.assinante.imagensHistoria > 1);
});

Deno.test("FLUX: pedido 4:3 com o filtro rígido e a imagem da resposta", () => {
  const corpo = pedidoFlux("a dragon reading");
  assertEquals(corpo.model, "flux-2-pro");
  assertEquals([corpo.width, corpo.height, corpo.safety_tolerance], [1024, 768, 1]);
  assertEquals(imagemBase64({ data: [{ b64_json: "AAEC" }] }), "AAEC");
  assertEquals(imagemBase64({ data: [] }), null);
});

Deno.test("erros dos provedores", () => {
  assertEquals(erroDoProvedor(400, '{"error":{"message":"Request Moderated"}}'), "BLOQUEADO");
  assertEquals(erroDoProvedor(400, "Content violated RAI policy (BingBlockList_Prompt)"), "BLOQUEADO");
  assertEquals(erroDoProvedor(529, "overloaded"), "OCUPADO");
  assertEquals(erroDoProvedor(429, "rate limit"), "OCUPADO");
  assertEquals(erroDoProvedor(401, "invalid x-api-key"), "FALHA_PROVEDOR");
});
