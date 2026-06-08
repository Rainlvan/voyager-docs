create table document_folders (
    id uuid primary key,
    name varchar(120) not null,
    parent_id uuid references document_folders(id) on delete restrict,
    created_by bigint not null references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index idx_document_folders_parent_name
    on document_folders(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));

create index idx_document_folders_parent_id on document_folders(parent_id);

alter table documents
    add column folder_id uuid references document_folders(id) on delete set null;

create index idx_documents_folder_id on documents(folder_id);
