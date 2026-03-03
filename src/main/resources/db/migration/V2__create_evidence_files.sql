create table evidence_files (
    id uuid primary key,
    case_id uuid not null,
    evidence_type varchar(64) not null,
    original_name varchar(255) not null,
    content_type varchar(128),
    storage_path varchar(1024) not null,
    size_bytes bigint not null,
    page_count integer not null,
    file_format varchar(32) not null,
    pdfa_compliant boolean not null,
    pdf_portfolio boolean not null,
    external_link_detected boolean not null,
    created_at timestamp with time zone not null,
    constraint fk_evidence_files_case foreign key (case_id) references cases(id)
);

create index idx_evidence_files_case on evidence_files(case_id);

