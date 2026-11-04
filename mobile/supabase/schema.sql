-- Run this in Supabase Dashboard > SQL Editor.
create table if not exists public.ledger_entries (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    item_name text not null check (char_length(item_name) between 1 and 120),
    amount bigint not null check (amount > 0),
    transaction_type text not null check (transaction_type in ('expense', 'income')),
    category text not null default '기타',
    occurred_on date not null default current_date,
    created_at timestamptz not null default now()
);

create index if not exists ledger_entries_user_date_idx
    on public.ledger_entries (user_id, occurred_on desc, created_at desc);

alter table public.ledger_entries enable row level security;
revoke all on table public.ledger_entries from anon, authenticated;
grant select, insert on table public.ledger_entries to authenticated;

drop policy if exists "Users can read their own ledger entries" on public.ledger_entries;
drop policy if exists "Users can create their own ledger entries" on public.ledger_entries;
create policy "Users can read their own ledger entries"
    on public.ledger_entries for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "Users can create their own ledger entries"
    on public.ledger_entries for insert to authenticated
    with check ((select auth.uid()) = user_id);




create table if not exists public.consumption_profiles (
    user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
    birth_date date not null check (birth_date <= current_date),
    occupation text not null check (occupation in ('학생', '직장인', '자영업자', '프리랜서', '구직 중', '기타')),
    school_type text,
    school_name text,
    grade smallint check (grade between 1 and 6),
    region text not null,
    updated_at timestamptz not null default now(),
    constraint consumption_profiles_student_fields check (
        (occupation = '학생' and school_type is not null
            and school_type in ('초등학교', '중학교', '고등학교', '대학교')
            and nullif(trim(school_name), '') is not null and grade is not null)
        or (occupation <> '학생' and school_type is null and school_name is null and grade is null)
    )
);

alter table public.consumption_profiles enable row level security;
revoke all on table public.consumption_profiles from anon, authenticated;
grant select, insert, update on table public.consumption_profiles to authenticated;

drop policy if exists "Users can read their own consumption profile" on public.consumption_profiles;
drop policy if exists "Users can create their own consumption profile" on public.consumption_profiles;
drop policy if exists "Users can update their own consumption profile" on public.consumption_profiles;
create policy "Users can read their own consumption profile"
    on public.consumption_profiles for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users can create their own consumption profile"
    on public.consumption_profiles for insert to authenticated
    with check ((select auth.uid()) = user_id);
create policy "Users can update their own consumption profile"
    on public.consumption_profiles for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

notify pgrst, 'reload schema';
