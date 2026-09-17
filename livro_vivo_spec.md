# Especificação Técnica do Projeto: Livro Vivo (Narrativas Interativas Adaptativas)

## 1\. Visão Geral e Proposta de Valor

- **Nome do Projeto**: Livro Vivo (Working Title)  
- **Modelo de Negócio**: B2C \- Micro-SaaS Mobile (Freemium com Assinatura Recorrente)  
- **Proposta de Valor**: Plataforma mobile que gera histórias infantis ilustradas, interativas e narradas por voz sob medida para o desenvolvimento cognitivo e emocional da criança. A criança toma decisões que alteram os rumos da história (nós de ramificação), enquanto os pais acompanham métricas de desenvolvimento e selecionam objetivos pedagógicos/comportamentais específicos.  
- **Público-Alvo**: Pais e cuidadores de crianças entre 3 e 9 anos.

---

## 2\. Stack Tecnológica e Arquitetura

### 2.1. Frontend Mobile (Android Nativo)

- **Linguagem**: Kotlin (100% nativo)  
- **Interface Declarativa**: Jetpack Compose com Material Design 3  
- **Arquitetura**: Clean Architecture \+ MVVM (UiState, StateFlow, Coroutines)  
- **Persistência Local (Offline-first)**:  
  - Room Database: Armazena perfis locais, histórias geradas, nós de escolha e progresso de leitura.  
  - Jetpack DataStore: Preferências do usuário, tokens de sessão e configurações gerais.  
- **Áudio e Reprodução**: AndroidX Media3 / ExoPlayer para execução de áudios de narração gerados via TTS.  
- **Injeção de Dependências**: Hilt ou Koin.  
- **Comunicação de Rede**: Retrofit / Ktor Client com Kotlinx Serialization.  
- **Monetização**: Google Play Billing Library v6+ (Assinaturas e Compras In-App).

### 2.2. Backend e Infraestrutura (Serverless)

- **Plataforma**: Supabase  
- **Autenticação**: Supabase Auth (Google One Tap e E-mail/Senha).  
- **Banco de Dados**: PostgreSQL com Row Level Security (RLS).  
- **Orquestração e Segurança**: Supabase Edge Functions (Deno / TypeScript) como intermediário seguro entre o app e as APIs de IA (as chaves de API nunca são expostas no cliente).

### 2.3. Modelos de Inteligência Artificial e Voz

- **LLM (Geração de Texto e Ramificações)**: Gemini 1.5 Flash ou Claude 3.5 Haiku com retorno forçado em JSON Schema estruturado.  
- **Narração (TTS)**: Google Cloud Text-to-Speech (vozes neurais PT-BR) ou ElevenLabs.

---

## 3\. Modelo de Dados (PostgreSQL / Supabase)

\-- Tabela de Usuários (Espelho do Auth do Supabase)

create table public.profiles (

    id uuid references auth.users not null primary key,

    email text not null,

    created\_at timestamp with time zone default timezone('utc'::text, now()) not null,

    is\_premium boolean default false not null,

    subscription\_status text default 'free' \-- 'free', 'trial', 'active', 'canceled'

);

&nbsp;

\-- Tabela de Perfis de Crianças

create table public.child\_profiles (

    id uuid default gen\_random\_uuid() primary key,

    user\_id uuid references public.profiles(id) on delete cascade not null,

    name text not null,

    age\_group text not null, \-- '3-5', '6-8', '9+'

    interests text\[\] default '{}', \-- \['dinossauros', 'espaço', 'animais'\]

    created\_at timestamp with time zone default timezone('utc'::text, now()) not null

);

&nbsp;

\-- Tabela de Histórias Geradas

create table public.stories (

    id uuid default gen\_random\_uuid() primary key,

    child\_id uuid references public.child\_profiles(id) on delete cascade not null,

    user\_id uuid references public.profiles(id) on delete cascade not null,

    title text not null,

    theme text not null, \-- 'medo\_do\_escuro', 'adaptacao\_escolar', 'aventura\_espacial'

    objective\_type text not null, \-- 'emocional', 'cognitivo', 'aventura'

    cover\_image\_url text,

    created\_at timestamp with time zone default timezone('utc'::text, now()) not null

);

&nbsp;

\-- Tabela de Capítulos e Nós de Ramificação

create table public.story\_chapters (

    id uuid default gen\_random\_uuid() primary key,

    story\_id uuid references public.stories(id) on delete cascade not null,

    chapter\_index int not null,

    content text not null,

    audio\_url text,

    choices jsonb default '\[\]'::jsonb, \-- Array de objetos: \[{"text": "...", "target\_chapter\_index": 2}\]

    is\_ending boolean default false not null

);

---

## 4\. Engenharia de Prompt e Contrato da Edge Function

### 4.1. Payload de Requisição do App para a Edge Function

{

  "childName": "Leo",

  "ageGroup": "4-6",

  "theme": "medo do escuro",

  "interests": \["dinossauros", "astronomia"\],

  "objectiveType": "emocional"

}

### 4.2. Prompt de Sistema (System Prompt)

Você é um autor de literatura infantil especializado em psicologia do desenvolvimento e contação de histórias interativas adaptativas.

Seu objetivo é criar uma história interativa e acolhedora em português do Brasil (PT-BR) adequada à faixa etária indicada.

&nbsp;

Diretrizes Obrigatórias:

1\. Adequação de Linguagem:

   \- Para 3-5 anos: frases curtas, vocabulário simples, foco em sons e repetições lúdicas.

   \- Para 6-8 anos: vocabulário um pouco mais rico, desafios de empatia e cooperação.

2\. Estrutura Interativa:

   \- A história deve conter entre 3 a 5 capítulos curtos.

   \- Ao final de cada capítulo intermediário, ofereça exatamente 2 opções de escolha construtivas para a criança guiar a narrativa.

   \- O capítulo final deve resolver o conflito emocional de forma positiva e segura.

3\. Formato de Saída:

   \- Responda estritamente com um JSON válido conforme o esquema solicitado, sem qualquer texto fora do JSON.

### 4.3. Formato de Resposta Estruturada (JSON Schema)

{

  "title": "Leo e o T-Rex Que Tinha Medo da Noite",

  "ageGroup": "4-6",

  "theme": "medo do escuro",

  "chapters": \[

    {

      "index": 1,

      "content": "Era uma vez Leo, um garotinho curioso que amava olhar para as estrelas...",

      "choices": \[

        { "text": "Procurar a lanterna mágica", "targetChapterIndex": 2 },

        { "text": "Chamar o amigo T-Rex de pelúcia", "targetChapterIndex": 3 }

      \],

      "isEnding": false

    },

    {

      "index": 2,

      "content": "Com sua lanterna mágica em mãos, Leo descobriu que as sombras eram apenas seus brinquedos dançando...",

      "choices": \[\],

      "isEnding": true

    },

    {

      "index": 3,

      "content": "O pequeno T-Rex abraçou Leo bem apertado e os dois perceberam que a noite trazia os sonhos mais bonitos...",

      "choices": \[\],

      "isEnding": true

    }

  \]

}

---

## 5\. Arquitetura do App Android

### 5.1. Estrutura de Pacotes Sugerida

com.livrovivo.app/

├── core/

│   ├── network/          \# Supabase client, Retrofit / Ktor

│   ├── database/         \# Room DB, DAOs e Entities

│   ├── audio/            \# ExoPlayer / Media3 Player Controller

│   └── ui/theme/         \# Design System infantil (Tipografia amigável, Cores acessíveis)

├── data/

│   ├── repository/       \# StoryRepository, ProfileRepository, BillingRepository

│   └── model/            \# DTOs de rede e Mappers

├── domain/

│   ├── model/            \# Modelos de domínio (Story, Chapter, Choice, Child)

│   └── usecase/          \# GenerateStoryUseCase, SaveStoryUseCase, PlayNarrationUseCase

└── presentation/

    ├── onboarding/       \# Boas-vindas e configuração do perfil da criança

    ├── home/             \# Estante de histórias, botão "Nova História"

    ├── creation/         \# Seleção de temas, objetivos e gatilho de geração

    ├── reader/           \# Tela de leitura interativa, transição de páginas e áudio

    └── paywall/          \# Tela de assinatura e benefícios do plano Premium

### 5.2. Padrão de UiState (Exemplo da Tela de Leitura)

data class ReaderUiState(

    val isLoading: Boolean \= false,

    val storyTitle: String \= "",

    val currentChapter: ChapterDomainModel? \= null,

    val isPlayingAudio: Boolean \= false,

    val playbackProgress: Float \= 0f,

    val isFinished: Boolean \= false,

    val errorMessage: String? \= null

)

---

## 6\. Estratégia de Monetização e Google Play Billing

- **SKUs In-App Subscription**:  
  - `livro_vivo_monthly`: R$ 29,90/mês  
  - `livro_vivo_annual`: R$ 199,90/ano (com 7 dias de trial gratuito)  
- **Lógica de Liberação**:  
  - Usuário Free: Limite de 2 histórias estáticas \+ 1 história gerada personalizada.  
  - Usuário Premium: Gerações ilimitadas, áudio TTS neural em todas as histórias e modo offline irrestrito.

---

## 7\. Políticas e Requisitos Google Play (Famílias e Crianças)

- **Conformidade com Families Policy**: O app deve atender estritamente aos requisitos para crianças da Play Store (sem links externos desprotegidos, sem anúncios inapropriados).  
- **Portão Parental (Parental Gate)**: Áreas de pagamento (Paywall), links para políticas de privacidade e configurações avançadas devem exigir uma ação que uma criança pequena não consiga realizar facilmente (ex.: resolver uma operação matemática simples por extenso).

---

## 8\. Roteiro de Implementação no Antigravity

1. **Passo 1**: Criar schema e Edge Function no Supabase para validar a geração de histórias com retorno JSON.  
2. **Passo 2**: Inicializar o projeto Android em Jetpack Compose com injeção de dependências e configuração de rede.  
3. **Passo 3**: Construir a camada de dados local com Room para cache de histórias (offline-first).  
4. **Passo 4**: Desenvolver o leitor de histórias com suporte a decisões de ramificação e integração com áudio.  
5. **Passo 5**: Integrar a biblioteca de Billing da Google Play e a barreira parental (*Parental Gate*).

&nbsp;