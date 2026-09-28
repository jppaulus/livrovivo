// Supabase Edge Function "ai-gateway": o servidor do Livro Vivo.
//
// O app fala só com este servidor. As chaves da Anthropic (texto) e da Microsoft (voz e ilustrações) ficam nos
// segredos do Supabase e nunca vão para o app. Cada pedido traz a sessão anônima do app (o Supabase confere a
// assinatura antes de chegar aqui: verify_jwt fica ligado no config.toml), passa pelos limites do plano
// (lv_reservar, na migração) e só então chama a IA. Nada da criança fica guardado: só contadores de uso.
//
//   POST .../ai-gateway/historia  { tipo, historiaId?, sistema, pedido, esquema }  -> { resultado, modelo }
//   POST .../ai-gateway/voz       { texto, voz, velocidade }                       -> PCM 16-bit mono 24 kHz, em partes
//   POST .../ai-gateway/imagem    { prompt, historiaId }                           -> image/jpeg
//   POST .../ai-gateway/estado    {}                                               -> uso da conta
//
// Erros: { "erro": { "codigo": "LIMITE_GRATIS", "mensagem": "..." } } (lista em regras.ts).

import Anthropic from "npm:@anthropic-ai/sdk@0.128";
import { createClient } from "npm:@supabase/supabase-js@2";
import { decodeBase64 } from "jsr:@std/encoding@1/base64";
import {
  CodigoErro,
  CUSTO_IMAGEM_USD,
  custoTextoUsd,
  custoVozUsd,
  ERROS,
  erroDoProvedor,
  estimativaTextoUsd,
  FLUX_PUBLICACAO,
  historiaIdValido,
  imagemBase64,
  lerRespostaClaude,
  LIMITES,
  MODELO_PADRAO,
  paraJsonSchema,
  pedidoClaude,
  pedidoFlux,
  ssml,
  TAMANHOS,
  TETO_DIARIO_PADRAO_USD,
  tipoTexto,
  velocidadeValida,
  VOZES,
} from "./regras.ts";

const MAX_CORPO = 64_000;

/** Chave secreta do projeto (a nova, sb_secret_...; ou a antiga service_role). Só o servidor a tem. */
function chaveSecreta(): string {
  const novas = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (novas) {
    try {
      const chaves = JSON.parse(novas) as Record<string, string>;
      const chave = chaves.default ?? Object.values(chaves)[0];
      if (chave) return chave;
    } catch {
      // usa a antiga abaixo
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
}

const banco = createClient(Deno.env.get("SUPABASE_URL") ?? "", chaveSecreta(), {
  auth: { persistSession: false, autoRefreshToken: false },
});

function tetoDiario(): number {
  const valor = Number(Deno.env.get("TETO_DIARIO_USD"));
  return Number.isFinite(valor) && valor > 0 ? valor : TETO_DIARIO_PADRAO_USD;
}

function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function erro(codigo: CodigoErro): Response {
  const { status, mensagem } = ERROS[codigo];
  return json({ erro: { codigo, mensagem } }, status);
}

/** Conta anônima do pedido. A assinatura do token já foi conferida pelo Supabase (verify_jwt). */
function usuario(req: Request): string | null {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const partes = token.split(".");
  if (partes.length !== 3) return null;
  try {
    const base64 = partes[1].replace(/-/g, "+").replace(/_/g, "/");
    const dados = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")));
    if (dados.role !== "authenticated" || typeof dados.sub !== "string") return null;
    if (typeof dados.exp === "number" && dados.exp * 1000 < Date.now()) return null;
    return dados.sub;
  } catch {
    return null;
  }
}

type Tipo = "abertura" | "continuacao" | "livro" | "voz" | "imagem";
type Reserva = { dia: string; nova: boolean };

async function reservar(
  user: string,
  tipo: Tipo,
  historia: string | null,
  quantidade: number,
  custo: number,
): Promise<Reserva | CodigoErro> {
  const { data, error } = await banco.rpc("lv_reservar", {
    p_user: user,
    p_tipo: tipo,
    p_historia: historia,
    p_quantidade: quantidade,
    p_custo: custo,
    p_limites: LIMITES,
    p_teto: tetoDiario(),
  });
  if (error) throw new Error(`lv_reservar: ${error.message}`);
  if (!data?.ok) return (data?.codigo ?? "PEDIDO_INVALIDO") as CodigoErro;
  return { dia: data.dia, nova: data.nova === true };
}

/** Devolve a cota quando a IA falhou. [custo] é o que ainda está reservado e não foi gasto de verdade. */
async function devolver(
  user: string,
  tipo: Tipo,
  historia: string | null,
  quantidade: number,
  custo: number,
  reserva: Reserva,
): Promise<void> {
  const { error } = await banco.rpc("lv_devolver", {
    p_user: user,
    p_tipo: tipo,
    p_historia: historia,
    p_quantidade: quantidade,
    p_custo: custo,
    p_dia: reserva.dia,
    p_nova: reserva.nova,
  });
  if (error) console.error("lv_devolver:", error.message);
}

async function acertar(user: string, reserva: Reserva, diferenca: number): Promise<void> {
  if (Math.abs(diferenca) < 0.000001) return;
  const { error } = await banco.rpc("lv_acertar", { p_user: user, p_dia: reserva.dia, p_diferenca: diferenca });
  if (error) console.error("lv_acertar:", error.message);
}

function lerJson(texto: string): Record<string, unknown> {
  try {
    const lido = JSON.parse(texto);
    return lido && typeof lido === "object" && !Array.isArray(lido) ? lido : {};
  } catch {
    return {};
  }
}

/** Espera no máximo [ms] pela resposta do provedor; depois que o áudio começa a chegar, não corta mais. */
async function buscarComPrazo(url: string, opcoes: RequestInit, ms: number): Promise<Response> {
  const controle = new AbortController();
  const prazo = setTimeout(() => controle.abort(), ms);
  try {
    return await fetch(url, { ...opcoes, signal: controle.signal });
  } finally {
    clearTimeout(prazo);
  }
}

async function historia(user: string, corpo: Record<string, unknown>): Promise<Response> {
  const chave = Deno.env.get("ANTHROPIC_API_KEY");
  if (!chave) return erro("NAO_CONFIGURADO");
  const tipo = tipoTexto(corpo.tipo);
  const { sistema, pedido } = corpo;
  if (
    !tipo || typeof sistema !== "string" || typeof pedido !== "string" || !sistema.trim() || !pedido.trim() ||
    sistema.length > TAMANHOS.sistema || pedido.length > TAMANHOS.pedido
  ) return erro("PEDIDO_INVALIDO");
  const historiaId = tipo === "livro" ? null : corpo.historiaId;
  if (historiaId !== null && !historiaIdValido(historiaId)) return erro("PEDIDO_INVALIDO");
  let esquema: Record<string, unknown>;
  try {
    if (JSON.stringify(corpo.esquema ?? null).length > TAMANHOS.esquema) return erro("PEDIDO_INVALIDO");
    esquema = paraJsonSchema(corpo.esquema);
  } catch {
    return erro("PEDIDO_INVALIDO");
  }

  const modelo = Deno.env.get("CLAUDE_MODELO") || MODELO_PADRAO;
  const estimativa = estimativaTextoUsd(modelo, sistema.length + pedido.length);
  const reserva = await reservar(user, tipo, historiaId, 1, estimativa);
  if (typeof reserva === "string") return erro(reserva);

  // Uma nova tentativa automática se a Anthropic estiver ocupada; a função inteira tem 150 s.
  const claude = new Anthropic({ apiKey: chave, timeout: 60_000, maxRetries: 1 });
  let resposta: Anthropic.Message;
  try {
    resposta = await claude.messages.create(pedidoClaude(modelo, sistema, pedido, esquema));
  } catch (e) {
    await devolver(user, tipo, historiaId, 1, estimativa, reserva);
    if (e instanceof Anthropic.RateLimitError || e instanceof Anthropic.InternalServerError) {
      console.error(`Claude ocupado: HTTP ${e.status}`);
      return erro("OCUPADO");
    }
    if (e instanceof Anthropic.APIError) {
      console.error(`Claude HTTP ${e.status}: ${e.message.slice(0, 300)}`);
      return erro("FALHA_PROVEDOR");
    }
    console.error("Claude sem resposta:", e);
    return erro("FALHA_PROVEDOR");
  }

  const { input_tokens, output_tokens } = resposta.usage;
  await acertar(user, reserva, custoTextoUsd(modelo, input_tokens, output_tokens) - estimativa);
  const lido = lerRespostaClaude(resposta as unknown as Record<string, unknown>);
  if ("erro" in lido) {
    // O Claude cobrou, então o custo fica; a cota da família volta.
    console.error(`Claude ${lido.erro}: stop_reason=${resposta.stop_reason}`);
    await devolver(user, tipo, historiaId, 1, 0, reserva);
    return erro(lido.erro);
  }
  console.log(`Claude ${resposta.model}: ${input_tokens} + ${output_tokens} tokens`);
  return json({ resultado: lido.json, modelo: resposta.model });
}

async function voz(user: string, corpo: Record<string, unknown>): Promise<Response> {
  const chave = Deno.env.get("AZURE_SPEECH_KEY");
  const regiao = Deno.env.get("AZURE_SPEECH_REGION");
  if (!chave || !regiao) return erro("NAO_CONFIGURADO");
  const { texto, voz, velocidade } = corpo;
  if (
    typeof texto !== "string" || !texto.trim() || texto.length > TAMANHOS.vozTexto ||
    typeof voz !== "string" || !(VOZES as readonly string[]).includes(voz) || !velocidadeValida(velocidade)
  ) return erro("PEDIDO_INVALIDO");

  const custo = custoVozUsd(voz, texto.length);
  const reserva = await reservar(user, "voz", null, texto.length, custo);
  if (typeof reserva === "string") return erro(reserva);

  let resposta: Response;
  try {
    resposta = await buscarComPrazo(`https://${regiao}.tts.speech.microsoft.com/cognitiveservices/v1`, {
      method: "POST",
      headers: {
        "Ocp-Apim-Subscription-Key": chave,
        "X-Microsoft-OutputFormat": "raw-24khz-16bit-mono-pcm",
        "Content-Type": "application/ssml+xml",
        "User-Agent": "LivroVivo",
      },
      body: ssml(voz, texto, velocidade),
    }, 15_000);
  } catch (e) {
    console.error("Azure Speech sem resposta:", e);
    await devolver(user, "voz", null, texto.length, custo, reserva);
    return erro("FALHA_PROVEDOR");
  }
  if (!resposta.ok || !resposta.body) {
    const detalhe = await resposta.text().catch(() => "");
    console.error(`Azure Speech HTTP ${resposta.status}: ${detalhe.slice(0, 300)}`);
    await devolver(user, "voz", null, texto.length, custo, reserva);
    return erro(erroDoProvedor(resposta.status, detalhe));
  }
  // O áudio segue para o app enquanto a Azure gera: o som começa antes de a página inteira ficar pronta.
  return new Response(resposta.body, {
    headers: { "Content-Type": "application/octet-stream", "X-Lv-Taxa": "24000" },
  });
}

async function imagem(user: string, corpo: Record<string, unknown>): Promise<Response> {
  const chave = Deno.env.get("AZURE_FOUNDRY_KEY");
  const endpoint = (Deno.env.get("AZURE_FOUNDRY_ENDPOINT") ?? "").trim().replace(/\/+$/, "");
  if (!chave || !endpoint.startsWith("https://")) return erro("NAO_CONFIGURADO");
  const { prompt, historiaId } = corpo;
  if (
    typeof prompt !== "string" || !prompt.trim() || prompt.length > TAMANHOS.imagemPrompt || !historiaIdValido(historiaId)
  ) return erro("PEDIDO_INVALIDO");

  const reserva = await reservar(user, "imagem", historiaId, 1, CUSTO_IMAGEM_USD);
  if (typeof reserva === "string") return erro(reserva);

  let resposta: Response;
  let texto: string;
  try {
    resposta = await buscarComPrazo(
      `${endpoint}/providers/blackforestlabs/v1/${FLUX_PUBLICACAO}?api-version=preview`,
      {
        method: "POST",
        headers: { "api-key": chave, "Content-Type": "application/json" },
        body: JSON.stringify(pedidoFlux(prompt)),
      },
      // Normal: 7 a 11 s. No teste de 28/09 uma chamada travou e só caiu em 90 s; com 45 s a criança vê logo o
      // desenho do app em vez de esperar.
      45_000,
    );
    texto = await resposta.text();
  } catch (e) {
    console.error("FLUX sem resposta:", e);
    await devolver(user, "imagem", historiaId, 1, CUSTO_IMAGEM_USD, reserva);
    return erro("FALHA_PROVEDOR");
  }
  if (!resposta.ok) {
    console.error(`FLUX HTTP ${resposta.status}: ${texto.slice(0, 300)}`);
    await devolver(user, "imagem", historiaId, 1, CUSTO_IMAGEM_USD, reserva);
    return erro(erroDoProvedor(resposta.status, texto));
  }
  const codificada = imagemBase64(lerJson(texto));
  if (!codificada) {
    await devolver(user, "imagem", historiaId, 1, CUSTO_IMAGEM_USD, reserva);
    return erro("RESPOSTA_INVALIDA");
  }
  return new Response(decodeBase64(codificada), { headers: { "Content-Type": "image/jpeg" } });
}

async function estado(user: string): Promise<Response> {
  const { data, error } = await banco.rpc("lv_estado", { p_user: user });
  if (error) throw new Error(`lv_estado: ${error.message}`);
  return json({ ...data, limites: LIMITES });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return erro("PEDIDO_INVALIDO");
  const user = usuario(req);
  if (!user) return erro("SESSAO");

  let corpo: Record<string, unknown>;
  try {
    const bruto = await req.text();
    if (bruto.length > MAX_CORPO) return erro("PEDIDO_INVALIDO");
    const lido = bruto.trim() ? JSON.parse(bruto) : {};
    if (!lido || typeof lido !== "object" || Array.isArray(lido)) return erro("PEDIDO_INVALIDO");
    corpo = lido;
  } catch {
    return erro("PEDIDO_INVALIDO");
  }

  try {
    switch (new URL(req.url).pathname.split("/").pop()) {
      case "historia":
        return await historia(user, corpo);
      case "voz":
        return await voz(user, corpo);
      case "imagem":
        return await imagem(user, corpo);
      case "estado":
        return await estado(user);
      default:
        return erro("PEDIDO_INVALIDO");
    }
  } catch (e) {
    console.error("ai-gateway:", e);
    return erro("FALHA_PROVEDOR");
  }
});
