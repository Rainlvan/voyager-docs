create table worker_heartbeats (
    worker_id varchar(120) primary key,
    status varchar(40) not null,
    current_job_id uuid,
    message text,
    started_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create index idx_worker_heartbeats_last_seen on worker_heartbeats(last_seen_at desc);
