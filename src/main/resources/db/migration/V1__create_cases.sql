create table cases (
    id uuid primary key,
    case_token varchar(64) not null unique,
    platform varchar(32) not null,
    product_scope varchar(64) not null,
    reason_code varchar(128),
    due_at date,
    card_network varchar(32),
    state varchar(32) not null,
    created_at timestamp with time zone not null
);

create index idx_cases_product_scope on cases (product_scope);

