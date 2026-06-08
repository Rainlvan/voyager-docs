create table users (
    id bigserial primary key,
    username varchar(80) not null unique,
    display_name varchar(120) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table ai_settings (
    id bigserial primary key,
    provider varchar(50) not null default 'dashscope',
    region varchar(80) not null default 'cn-beijing',
    api_key_ciphertext text,
    chat_model varchar(120) not null,
    text_embedding_model varchar(120) not null,
    text_embedding_dimension int not null,
    multimodal_embedding_model varchar(120) not null,
    multimodal_embedding_dimension int not null,
    rerank_model varchar(120) not null,
    multimodal_rerank_model varchar(120) not null,
    updated_by bigint references users(id),
    updated_at timestamptz not null default now()
);

create table documents (
    id uuid primary key,
    title varchar(500) not null,
    original_filename varchar(500) not null,
    content_type varchar(255),
    file_size bigint not null,
    object_key varchar(1000) not null,
    status varchar(40) not null,
    uploaded_by bigint not null references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index idx_documents_title on documents using gin (to_tsvector('simple', title));
create index idx_documents_uploaded_by on documents(uploaded_by);
create index idx_documents_status on documents(status);

create table ingestion_jobs (
    id uuid primary key,
    document_id uuid not null references documents(id),
    status varchar(40) not null,
    attempt_count int not null default 0,
    error_message text,
    locked_by varchar(120),
    locked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ingestion_jobs_status on ingestion_jobs(status, created_at);
create index idx_ingestion_jobs_document_id on ingestion_jobs(document_id);

create table document_chunks (
    id uuid primary key,
    document_id uuid not null references documents(id),
    chunk_index int not null,
    page_number int,
    content text not null,
    token_estimate int not null default 0,
    opensearch_id varchar(255),
    created_at timestamptz not null default now(),
    unique(document_id, chunk_index)
);

create table visual_units (
    id uuid primary key,
    document_id uuid not null references documents(id),
    page_number int,
    kind varchar(40) not null,
    object_key varchar(1000),
    caption text,
    ocr_text text,
    opensearch_id varchar(255),
    created_at timestamptz not null default now()
);

create table chat_sessions (
    id uuid primary key,
    title varchar(300) not null,
    created_by bigint not null references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table chat_messages (
    id uuid primary key,
    session_id uuid not null references chat_sessions(id),
    role varchar(30) not null,
    content text not null,
    citations_json text,
    created_at timestamptz not null default now()
);

insert into ai_settings (
    provider,
    region,
    chat_model,
    text_embedding_model,
    text_embedding_dimension,
    multimodal_embedding_model,
    multimodal_embedding_dimension,
    rerank_model,
    multimodal_rerank_model
) values (
    'dashscope',
    'cn-beijing',
    'qwen3.6-flash',
    'text-embedding-v4',
    1024,
    'qwen3-vl-embedding',
    2560,
    'gte-rerank-v2',
    'qwen3-vl-rerank'
);
