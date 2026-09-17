-- Migração Inicial para o Livro Vivo (Supabase PostgreSQL)
-- Criação de tabelas, índices e políticas de Row Level Security (RLS)

-- 1. Tabela de Perfis de Pais/Usuários (vinculada ao Supabase Auth)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID REFERENCES auth.users NOT NULL PRIMARY KEY,
    email TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    is_premium BOOLEAN DEFAULT false NOT NULL,
    subscription_status TEXT DEFAULT 'free' NOT NULL -- 'free', 'trial', 'active', 'canceled'
);

-- 2. Tabela de Perfis de Crianças
CREATE TABLE IF NOT EXISTS public.child_profiles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    name TEXT NOT NULL,
    age_group TEXT NOT NULL, -- '3-5', '6-8', '9+'
    interests TEXT[] DEFAULT '{}' NOT NULL, -- ['dinossauros', 'espaço', 'animais']
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Tabela de Histórias Geradas
CREATE TABLE IF NOT EXISTS public.stories (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    child_id UUID REFERENCES public.child_profiles(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    title TEXT NOT NULL,
    theme TEXT NOT NULL, -- 'medo_do_escuro', 'adaptacao_escolar', 'aventura_espacial', etc.
    objective_type TEXT NOT NULL, -- 'emocional', 'cognitivo', 'aventura'
    cover_image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 4. Tabela de Capítulos e Nós de Ramificação
CREATE TABLE IF NOT EXISTS public.story_chapters (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    story_id UUID REFERENCES public.stories(id) ON DELETE CASCADE NOT NULL,
    chapter_index INT NOT NULL,
    content TEXT NOT NULL,
    audio_url TEXT,
    choices JSONB DEFAULT '[]'::jsonb NOT NULL, -- [{"text": "...", "target_chapter_index": 2}]
    is_ending BOOLEAN DEFAULT false NOT NULL
);

-- 5. Índices de Desempenho
CREATE INDEX IF NOT EXISTS idx_child_profiles_user_id ON public.child_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_stories_user_id ON public.stories(user_id);
CREATE INDEX IF NOT EXISTS idx_stories_child_id ON public.stories(child_id);
CREATE INDEX IF NOT EXISTS idx_story_chapters_story_id ON public.story_chapters(story_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_story_chapter_unique ON public.story_chapters(story_id, chapter_index);

-- 6. Habilitação de Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.child_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_chapters ENABLE ROW LEVEL SECURITY;

-- 7. Políticas de Segurança RLS (Somente o próprio usuário tem acesso aos seus dados)

-- Políticas para Profiles
CREATE POLICY "Usuários podem ver seu próprio perfil" 
    ON public.profiles FOR SELECT 
    USING (auth.uid() = id);

CREATE POLICY "Usuários podem atualizar seu próprio perfil" 
    ON public.profiles FOR UPDATE 
    USING (auth.uid() = id);

-- Políticas para Child Profiles
CREATE POLICY "Usuários podem ver perfis de seus filhos" 
    ON public.child_profiles FOR SELECT 
    USING (auth.uid() = user_id);

CREATE POLICY "Usuários podem criar perfis de seus filhos" 
    ON public.child_profiles FOR INSERT 
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Usuários podem atualizar perfis de seus filhos" 
    ON public.child_profiles FOR UPDATE 
    USING (auth.uid() = user_id);

CREATE POLICY "Usuários podem excluir perfis de seus filhos" 
    ON public.child_profiles FOR DELETE 
    USING (auth.uid() = user_id);

-- Políticas para Stories
CREATE POLICY "Usuários podem ver suas próprias histórias" 
    ON public.stories FOR SELECT 
    USING (auth.uid() = user_id);

CREATE POLICY "Usuários podem criar histórias" 
    ON public.stories FOR INSERT 
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Usuários podem deletar suas histórias" 
    ON public.stories FOR DELETE 
    USING (auth.uid() = user_id);

-- Políticas para Story Chapters
CREATE POLICY "Usuários podem ver capítulos de suas histórias" 
    ON public.story_chapters FOR SELECT 
    USING (
        EXISTS (
            SELECT 1 FROM public.stories 
            WHERE public.stories.id = public.story_chapters.story_id 
              AND public.stories.user_id = auth.uid()
        )
    );

CREATE POLICY "Usuários podem inserir capítulos de suas histórias" 
    ON public.story_chapters FOR INSERT 
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.stories 
            WHERE public.stories.id = public.story_chapters.story_id 
              AND public.stories.user_id = auth.uid()
        )
    );

-- 8. Trigger para auto-criação de Profile no signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email)
    VALUES (NEW.id, NEW.email);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
