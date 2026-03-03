create table audit_logs (
    id uuid primary key,
    case_id uuid not null,
    actor_type varchar(32) not null,
    action varchar(64) not null,
    metadata varchar(2000),
    created_at timestamp with time zone not null,
    constraint fk_audit_logs_case foreign key (case_id) references cases(id)
);

create index idx_audit_logs_case on audit_logs(case_id);
create index idx_audit_logs_created_at on audit_logs(created_at);

