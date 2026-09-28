-- Servidor do Livro Vivo (27/09/2026).
-- Guarda só contadores de uso por conta anônima: nada da criança (nome, aparência, texto das histórias) fica aqui.
-- O app não lê nem escreve nestas tabelas: só a função ai-gateway, com a chave secreta do projeto.

create table if not exists public.lv_contas (
    user_id uuid primary key references auth.users (id) on delete cascade,
    -- Por enquanto marcado à mão para quem testa; depois vem da compra conferida na Google Play.
    assinante boolean not null default false,
    assinante_ate timestamptz,
    criada_em timestamptz not null default now()
);

create table if not exists public.lv_historias (
    user_id uuid not null references auth.users (id) on delete cascade,
    historia_id text not null,
    -- 'aventura' conta no limite de histórias; 'extra' (ilustração de livro ou de história sem IA) não conta.
    tipo text not null default 'aventura',
    criada_em timestamptz not null default now(),
    textos int not null default 0,
    imagens int not null default 0,
    primary key (user_id, historia_id)
);

create table if not exists public.lv_uso_diario (
    dia date not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    textos int not null default 0,
    caracteres_voz int not null default 0,
    imagens int not null default 0,
    custo_usd numeric(12, 5) not null default 0,
    primary key (dia, user_id)
);

-- Gasto somado de todas as contas no dia: acima do teto, o servidor para até o dia seguinte.
create table if not exists public.lv_gasto_dia (
    dia date primary key,
    custo_usd numeric(12, 5) not null default 0
);

alter table public.lv_contas enable row level security;
alter table public.lv_historias enable row level security;
alter table public.lv_uso_diario enable row level security;
alter table public.lv_gasto_dia enable row level security;
-- Sem políticas de acesso: com RLS ligado e nenhuma política, o app (chave pública) não enxerga nada.

create or replace function public.lv_hoje() returns date
language sql stable set search_path = public as $$
    select (now() at time zone 'America/Sao_Paulo')::date
$$;

create or replace function public.lv_falha(p_codigo text) returns jsonb
language sql immutable as $$
    select jsonb_build_object('ok', false, 'codigo', p_codigo)
$$;

-- Confere os limites e já reserva o uso (texto, voz ou imagem) numa transação só.
-- Se a chamada à IA falhar, o servidor devolve a reserva com lv_devolver; se der certo, acerta o custo com lv_acertar.
create or replace function public.lv_reservar(
    p_user uuid,
    p_tipo text,
    p_historia text,
    p_quantidade int,
    p_custo numeric,
    p_limites jsonb,
    p_teto numeric
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    v_hoje date := lv_hoje();
    v_conta lv_contas%rowtype;
    v_assinante boolean;
    v_limites jsonb;
    v_uso lv_uso_diario%rowtype;
    v_historia lv_historias%rowtype;
    v_gasto numeric;
    v_nova boolean := false;
    v_contagem int;
begin
    insert into lv_contas (user_id) values (p_user) on conflict (user_id) do nothing;
    select * into v_conta from lv_contas where user_id = p_user for update;
    v_assinante := v_conta.assinante and (v_conta.assinante_ate is null or v_conta.assinante_ate > now());
    v_limites := p_limites -> (case when v_assinante then 'assinante' else 'gratis' end);

    insert into lv_gasto_dia (dia) values (v_hoje) on conflict (dia) do nothing;
    select custo_usd into v_gasto from lv_gasto_dia where dia = v_hoje for update;
    if v_gasto + p_custo > p_teto then
        return lv_falha('TETO_DIARIO');
    end if;

    insert into lv_uso_diario (dia, user_id) values (v_hoje, p_user) on conflict (dia, user_id) do nothing;
    select * into v_uso from lv_uso_diario where dia = v_hoje and user_id = p_user for update;

    if p_historia is not null then
        select * into v_historia from lv_historias
        where user_id = p_user and historia_id = p_historia for update;
    end if;

    if p_tipo in ('abertura', 'continuacao', 'livro') then
        if p_tipo = 'livro' and not v_assinante then
            return lv_falha('SO_ASSINANTE');
        end if;
        if v_uso.textos >= (v_limites ->> 'textosDia')::int then
            return lv_falha('LIMITE_DIA');
        end if;
        if p_tipo = 'abertura' and v_historia.historia_id is null then
            if v_assinante then
                select count(*) into v_contagem from lv_historias
                where user_id = p_user and tipo = 'aventura'
                  and criada_em >= (date_trunc('month', now() at time zone 'America/Sao_Paulo') at time zone 'America/Sao_Paulo');
                if v_contagem >= (v_limites ->> 'historiasMes')::int then
                    return lv_falha('LIMITE_MES');
                end if;
            else
                select count(*) into v_contagem from lv_historias where user_id = p_user and tipo = 'aventura';
                if v_contagem >= (v_limites ->> 'historias')::int then
                    return lv_falha('LIMITE_GRATIS');
                end if;
            end if;
            insert into lv_historias (user_id, historia_id) values (p_user, p_historia) returning * into v_historia;
            v_nova := true;
        elsif p_tipo = 'continuacao' and v_historia.historia_id is null then
            return lv_falha('HISTORIA_DESCONHECIDA');
        end if;
        if v_historia.historia_id is not null then
            if v_historia.textos >= (v_limites ->> 'textosHistoria')::int then
                return lv_falha('LIMITE_HISTORIA');
            end if;
            update lv_historias set textos = textos + 1 where user_id = p_user and historia_id = p_historia;
        end if;
        update lv_uso_diario set textos = textos + 1, custo_usd = custo_usd + p_custo
        where dia = v_hoje and user_id = p_user;

    elsif p_tipo = 'voz' then
        if v_uso.caracteres_voz + p_quantidade > (v_limites ->> 'caracteresVozDia')::int then
            return lv_falha('LIMITE_DIA');
        end if;
        update lv_uso_diario set caracteres_voz = caracteres_voz + p_quantidade, custo_usd = custo_usd + p_custo
        where dia = v_hoje and user_id = p_user;

    elsif p_tipo = 'imagem' then
        if v_uso.imagens >= (v_limites ->> 'imagensDia')::int then
            return lv_falha('LIMITE_DIA');
        end if;
        if v_historia.historia_id is null then
            -- No plano grátis, só a capa de uma história criada com IA ganha ilustração de IA.
            if not v_assinante then
                return lv_falha('SO_ASSINANTE');
            end if;
            insert into lv_historias (user_id, historia_id, tipo) values (p_user, p_historia, 'extra')
            returning * into v_historia;
            v_nova := true;
        end if;
        if v_historia.imagens >= (v_limites ->> 'imagensHistoria')::int then
            return lv_falha(case when v_assinante then 'LIMITE_HISTORIA' else 'SO_ASSINANTE' end);
        end if;
        update lv_historias set imagens = imagens + 1 where user_id = p_user and historia_id = p_historia;
        update lv_uso_diario set imagens = imagens + 1, custo_usd = custo_usd + p_custo
        where dia = v_hoje and user_id = p_user;

    else
        return lv_falha('PEDIDO_INVALIDO');
    end if;

    update lv_gasto_dia set custo_usd = custo_usd + p_custo where dia = v_hoje;
    return jsonb_build_object('ok', true, 'assinante', v_assinante, 'nova', v_nova, 'dia', v_hoje);
end;
$$;

-- Desfaz uma reserva quando a IA falhou: a família não perde cota por erro do serviço.
create or replace function public.lv_devolver(
    p_user uuid,
    p_tipo text,
    p_historia text,
    p_quantidade int,
    p_custo numeric,
    p_dia date,
    p_nova boolean
) returns void
language plpgsql security definer set search_path = public as $$
begin
    if p_tipo in ('abertura', 'continuacao', 'livro') then
        update lv_uso_diario set textos = greatest(0, textos - 1), custo_usd = greatest(0, custo_usd - p_custo)
        where dia = p_dia and user_id = p_user;
        update lv_historias set textos = greatest(0, textos - 1) where user_id = p_user and historia_id = p_historia;
    elsif p_tipo = 'voz' then
        update lv_uso_diario set caracteres_voz = greatest(0, caracteres_voz - p_quantidade),
            custo_usd = greatest(0, custo_usd - p_custo)
        where dia = p_dia and user_id = p_user;
    elsif p_tipo = 'imagem' then
        update lv_uso_diario set imagens = greatest(0, imagens - 1), custo_usd = greatest(0, custo_usd - p_custo)
        where dia = p_dia and user_id = p_user;
        update lv_historias set imagens = greatest(0, imagens - 1) where user_id = p_user and historia_id = p_historia;
    end if;
    if p_nova then
        delete from lv_historias
        where user_id = p_user and historia_id = p_historia and textos = 0 and imagens = 0;
    end if;
    update lv_gasto_dia set custo_usd = greatest(0, custo_usd - p_custo) where dia = p_dia;
end;
$$;

-- Troca a estimativa da reserva pelo custo real (tokens usados pelo Claude).
create or replace function public.lv_acertar(p_user uuid, p_dia date, p_diferenca numeric) returns void
language sql security definer set search_path = public as $$
    update lv_uso_diario set custo_usd = greatest(0, custo_usd + p_diferenca) where dia = p_dia and user_id = p_user;
    update lv_gasto_dia set custo_usd = greatest(0, custo_usd + p_diferenca) where dia = p_dia;
$$;

-- O que a conta já usou (para o app mostrar e para conferir nos testes).
create or replace function public.lv_estado(p_user uuid) returns jsonb
language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'assinante', coalesce((select assinante and (assinante_ate is null or assinante_ate > now())
                               from lv_contas where user_id = p_user), false),
        'historias', (select count(*) from lv_historias where user_id = p_user and tipo = 'aventura'),
        'historiasMes', (select count(*) from lv_historias where user_id = p_user and tipo = 'aventura'
                         and criada_em >= (date_trunc('month', now() at time zone 'America/Sao_Paulo') at time zone 'America/Sao_Paulo')),
        'hoje', (select jsonb_build_object('textos', textos, 'caracteresVoz', caracteres_voz, 'imagens', imagens)
                 from lv_uso_diario where dia = lv_hoje() and user_id = p_user)
    )
$$;

revoke all on function public.lv_reservar(uuid, text, text, int, numeric, jsonb, numeric) from public, anon, authenticated;
revoke all on function public.lv_devolver(uuid, text, text, int, numeric, date, boolean) from public, anon, authenticated;
revoke all on function public.lv_acertar(uuid, date, numeric) from public, anon, authenticated;
revoke all on function public.lv_estado(uuid) from public, anon, authenticated;
grant execute on function public.lv_reservar(uuid, text, text, int, numeric, jsonb, numeric) to service_role;
grant execute on function public.lv_devolver(uuid, text, text, int, numeric, date, boolean) to service_role;
grant execute on function public.lv_acertar(uuid, date, numeric) to service_role;
grant execute on function public.lv_estado(uuid) to service_role;
