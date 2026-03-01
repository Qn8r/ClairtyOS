create table if not exists public.community_comments (
  id uuid primary key default gen_random_uuid(),
  post_id text not null,
  author_id text not null,
  author_name text not null default 'Player',
  body text not null,
  created_at timestamptz not null default now()
);

create index if not exists community_comments_post_created_idx
  on public.community_comments (post_id, created_at asc);

create table if not exists public.community_comment_votes (
  comment_id uuid not null,
  user_id text not null,
  vote integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (comment_id, user_id),
  constraint community_comment_votes_vote_check check (vote between -1 and 1)
);

create index if not exists community_comment_votes_comment_idx
  on public.community_comment_votes (comment_id);

alter table public.community_comments enable row level security;
alter table public.community_comment_votes enable row level security;

drop policy if exists community_comments_select_policy on public.community_comments;
create policy community_comments_select_policy
on public.community_comments
for select
to anon, authenticated
using (true);

drop policy if exists community_comments_insert_policy on public.community_comments;
create policy community_comments_insert_policy
on public.community_comments
for insert
to anon, authenticated
with check (true);

drop policy if exists community_comment_votes_select_policy on public.community_comment_votes;
create policy community_comment_votes_select_policy
on public.community_comment_votes
for select
to anon, authenticated
using (true);

drop policy if exists community_comment_votes_insert_policy on public.community_comment_votes;
create policy community_comment_votes_insert_policy
on public.community_comment_votes
for insert
to anon, authenticated
with check (true);

drop policy if exists community_comment_votes_update_policy on public.community_comment_votes;
create policy community_comment_votes_update_policy
on public.community_comment_votes
for update
to anon, authenticated
using (true)
with check (true);
