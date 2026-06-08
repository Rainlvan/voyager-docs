create table audit_events (
    id uuid primary key,
    actor_user_id bigint references users(id),
    actor_username varchar(80),
    actor_role varchar(30),
    ip_address varchar(100),
    user_agent varchar(500),
    action varchar(100) not null,
    resource_type varchar(80),
    resource_id varchar(160),
    success boolean not null,
    summary text,
    created_at timestamptz not null default now()
);

create index idx_audit_events_created_at on audit_events(created_at desc);
create index idx_audit_events_actor_user_id on audit_events(actor_user_id);
create index idx_audit_events_action on audit_events(action);
create index idx_audit_events_success on audit_events(success);

create table login_rate_limits (
    id bigserial primary key,
    username varchar(80) not null,
    ip_address varchar(100) not null,
    failure_count int not null default 0,
    first_failed_at timestamptz,
    last_failed_at timestamptz,
    locked_until timestamptz,
    unique(username, ip_address)
);

create index idx_login_rate_limits_locked_until on login_rate_limits(locked_until);

create table backup_settings (
    id bigserial primary key,
    enabled boolean not null default false,
    daily_time varchar(5) not null default '02:00',
    updated_by bigint references users(id),
    updated_at timestamptz not null default now()
);

insert into backup_settings(enabled, daily_time)
values (false, '02:00');

create table backup_runs (
    id uuid primary key,
    status varchar(40) not null,
    trigger_type varchar(40) not null,
    started_by bigint references users(id),
    backup_filename varchar(500),
    backup_path varchar(1000),
    file_size bigint not null default 0,
    sha256 varchar(64),
    object_count int not null default 0,
    error_message text,
    manifest_json text,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    created_at timestamptz not null default now()
);

create index idx_backup_runs_created_at on backup_runs(created_at desc);
create index idx_backup_runs_status on backup_runs(status);

create table system_maintenance (
    id smallint primary key,
    enabled boolean not null default false,
    reason varchar(500),
    started_at timestamptz,
    started_by bigint references users(id)
);

insert into system_maintenance(id, enabled)
values (1, false);
