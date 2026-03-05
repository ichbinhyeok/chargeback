create table payments (
    id uuid primary key,
    case_id uuid not null,
    provider varchar(32) not null,
    checkout_session_id varchar(128),
    payment_intent_id varchar(128),
    status varchar(32) not null,
    amount_cents bigint not null,
    currency varchar(16) not null,
    customer_email varchar(255),
    created_at timestamp with time zone not null,
    paid_at timestamp with time zone,
    constraint fk_payments_case foreign key (case_id) references cases(id)
);

create unique index ux_payments_checkout_session_id on payments(checkout_session_id);
create index idx_payments_case on payments(case_id);
create index idx_payments_status on payments(status);
