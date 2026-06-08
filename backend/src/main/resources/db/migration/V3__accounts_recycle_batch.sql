alter table users
    add column avatar_object_key varchar(1000),
    add column avatar_content_type varchar(120),
    add column deleted_at timestamptz;

create index idx_users_deleted_at on users(deleted_at);

alter table documents
    add column recycled_at timestamptz,
    add column recycled_by bigint references users(id);

create index idx_documents_recycled_at on documents(recycled_at);

alter table ai_settings
    add column embedding_invocation_mode varchar(30) not null default 'REALTIME';

create table embedding_batches (
    id uuid primary key,
    ingestion_job_id uuid not null references ingestion_jobs(id),
    document_id uuid not null references documents(id),
    provider_batch_id varchar(255) not null,
    input_file_id varchar(255),
    output_file_id varchar(255),
    error_file_id varchar(255),
    status varchar(80) not null,
    request_object_key varchar(1000),
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_embedding_batches_status on embedding_batches(status, created_at);
create index idx_embedding_batches_document_id on embedding_batches(document_id);
