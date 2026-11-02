-- Run this in Supabase Dashboard > SQL Editor.
create table if not exists public.voice_entries (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    transcript text not null check (char_length(transcript) between 1 and 5000),
    occurred_on date not null default current_date,
    created_at timestamptz not null default now()
);

alter table public.voice_entries
    add column if not exists occurred_on date not null default current_date;

create index if not exists voice_entries_user_created_idx
    on public.voice_entries (user_id, created_at desc);

alter table public.voice_entries enable row level security;

-- Keep the table off the anonymous API role and grant only app operations.
revoke all on table public.voice_entries from anon, authenticated;
grant select, insert on table public.voice_entries to authenticated;

drop policy if exists "Users can read their own voice entries" on public.voice_entries;
drop policy if exists "Users can create their own voice entries" on public.voice_entries;
create policy "Users can read their own voice entries"
    on public.voice_entries for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "Users can create their own voice entries"
    on public.voice_entries for insert to authenticated
    with check ((select auth.uid()) = user_id);

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

notify pgrst, 'reload schema';

