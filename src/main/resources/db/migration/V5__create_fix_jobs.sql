create table fix_jobs (
    id uuid primary key,
    case_id uuid not null,
    status varchar(32) not null,
    summary varchar(500) not null,
    fail_code varchar(64),
    fail_message varchar(2000),
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    constraint fk_fix_jobs_case foreign key (case_id) references cases(id)
);

create index idx_fix_jobs_case on fix_jobs(case_id);
create index idx_fix_jobs_created_at on fix_jobs(created_at);
