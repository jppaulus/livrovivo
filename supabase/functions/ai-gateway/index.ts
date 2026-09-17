// Supabase Edge Function: ai-gateway
//
// Proxy seguro entre o app Livro Vivo e as APIs de IA. As chaves ficam nos secrets do
// Supabase e nunca vão para o aplicativo. Os prompts continuam no app; o servidor valida
// modelos/rotas, limita o tamanho das requisições e reforça os filtros de segurança infantis.
//
// Requisições aceitas (POST, JSON):
//   { "provider": "gemini", "model": "gemini-2.5-flash", "payload": { ...corpo do generateContent } }
//   { "provider": "elevenlabs", "method": "POST", "path": "/v1/text-to-speech/<voiceId>?output_format=mp3_44100_128", "payload": {...} }
//   { "provider": "elevenlabs", "method": "GET", "path": "/v2/voices?page_size=100" }
//
// A resposta do provedor é repassada com o mesmo status HTTP, para o app tratar erros igual ao modo direto.

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const ELEVENLABS_BASE = "https://api.elevenlabs.io";

const MAX_BODY_BYTES = 3_000_000; // ilustração de referência em JPEG cabe com folga
const MAX_TTS_CHARS = 5_000;
const GEMINI_MODEL = /^gemini-[a-z0-9.\-]{3,60}$/;
const ELEVENLABS_ROUTES = [
  /^\/v1\/text-to-speech\/[A-Za-z0-9]{8,40}\?output_format=[a-z0-9_]{3,30}$/,
  /^\/v2\/voices(\?[A-Za-z0-9_=&%.\-]{0,400})?$/,
];

const CHILD_SAFETY_SETTINGS = [
  "HARM_CATEGORY_HARASSMENT",
  "HARM_CATEGORY_HATE_SPEECH",
  "HARM_CATEGORY_SEXUALLY_EXPLICIT",
  "HARM_CATEGORY_DANGEROUS_CONTENT",
].map((category) => ({ category, threshold: "BLOCK_MEDIUM_AND_ABOVE" }));

type GatewayRequest = {
  provider?: string;
  model?: string;
  method?: string;
  path?: string;
  payload?: Record<string, unknown>;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function passthrough(upstream: Response): Response {
  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      ...corsHeaders,
      "Content-Type": upstream.headers.get("Content-Type") ?? "application/octet-stream",
    },
  });
}

async function proxyGemini(request: GatewayRequest): Promise<Response> {
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) {
    return json({ error: { code: 501, status: "NOT_CONFIGURED", message: "GEMINI_API_KEY não configurada no servidor." } }, 501);
  }
  if (typeof request.model !== "string" || !GEMINI_MODEL.test(request.model)) {
    return json({ error: { code: 400, status: "INVALID_ARGUMENT", message: "Modelo inválido." } }, 400);
  }
  if (!request.payload || typeof request.payload !== "object") {
    return json({ error: { code: 400, status: "INVALID_ARGUMENT", message: "payload obrigatório." } }, 400);
  }

  const payload = { ...request.payload };
  const generationConfig = (payload.generationConfig ?? {}) as Record<string, unknown>;
  const modalities = (generationConfig.responseModalities ?? []) as string[];
  const isSpeech = modalities.includes("AUDIO");
  // Reforça os filtros de segurança em texto e imagem (TTS apenas lê o texto já gerado).
  if (!isSpeech && !payload.safetySettings) {
    payload.safetySettings = CHILD_SAFETY_SETTINGS;
  }

  const upstream = await fetch(`${GEMINI_BASE}/${request.model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(payload),
  });
  return passthrough(upstream);
}

async function proxyElevenLabs(request: GatewayRequest): Promise<Response> {
  const apiKey = Deno.env.get("ELEVENLABS_API_KEY");
  if (!apiKey) {
    return json({ detail: { status: "not_configured", message: "ELEVENLABS_API_KEY não configurada no servidor." } }, 501);
  }
  const path = String(request.path ?? "");
  if (!ELEVENLABS_ROUTES.some((route) => route.test(path))) {
    return json({ detail: { status: "invalid_path", message: "Rota não permitida." } }, 400);
  }
  const method = request.method === "GET" ? "GET" : "POST";
  if (method === "POST") {
    const text = String((request.payload ?? {}).text ?? "");
    if (!text.trim() || text.length > MAX_TTS_CHARS) {
      return json({ detail: { status: "invalid_text", message: "Texto vazio ou longo demais." } }, 400);
    }
  }

  const upstream = await fetch(ELEVENLABS_BASE + path, {
    method,
    headers: {
      "xi-api-key": apiKey,
      ...(method === "POST" ? { "Content-Type": "application/json" } : {}),
    },
    body: method === "POST" ? JSON.stringify(request.payload ?? {}) : undefined,
  });
  return passthrough(upstream);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: { code: 405, message: "Use POST." } }, 405);
  }

  const declaredLength = Number(req.headers.get("content-length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) {
    return json({ error: { code: 413, message: "Requisição muito grande." } }, 413);
  }

  let request: GatewayRequest;
  try {
    const raw = await req.text();
    if (raw.length > MAX_BODY_BYTES) {
      return json({ error: { code: 413, message: "Requisição muito grande." } }, 413);
    }
    request = JSON.parse(raw);
  } catch {
    return json({ error: { code: 400, message: "JSON inválido." } }, 400);
  }

  try {
    switch (request.provider) {
      case "gemini":
        return await proxyGemini(request);
      case "elevenlabs":
        return await proxyElevenLabs(request);
      default:
        return json({ error: { code: 400, message: "Provedor desconhecido." } }, 400);
    }
  } catch (error) {
    console.error("ai-gateway:", error);
    return json({ error: { code: 502, status: "UNAVAILABLE", message: "Falha ao contatar o provedor de IA." } }, 502);
  }
});
