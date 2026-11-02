-- Run this in Supabase Dashboard > SQL Editor.
create table if not exists public.voice_entries (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    transcript text not null check (char_length(transcript) between 1 and 5000),
    created_at timestamptz not null default now()
);

create index if not exists voice_entries_user_created_idx
    on public.voice_entries (user_id, created_at desc);

alter table public.voice_entries enable row level security;

create policy "Users can read their own voice entries"
    on public.voice_entries for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "Users can create their own voice entries"
    on public.voice_entries for insert to authenticated
    with check ((select auth.uid()) = user_id);

create policy "Users can delete their own voice entries"
    on public.voice_entries for delete to authenticated
    using ((select auth.uid()) = user_id);
