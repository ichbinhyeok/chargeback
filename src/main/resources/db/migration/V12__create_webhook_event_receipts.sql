create table webhook_event_receipts (
    id uuid primary key,
    case_id uuid not null,
    provider varchar(32) not null,
    event_type varchar(128) not null,
    event_id varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint fk_webhook_event_receipts_case foreign key (case_id) references cases(id)
);

create unique index ux_webhook_event_receipts_provider_type_id
    on webhook_event_receipts(provider, event_type, event_id);

create index idx_webhook_event_receipts_case
    on webhook_event_receipts(case_id);
