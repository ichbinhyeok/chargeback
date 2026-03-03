create table validation_runs (
    id uuid primary key,
    case_id uuid not null,
    run_no integer not null,
    is_passed boolean not null,
    source varchar(32) not null,
    early_submit boolean not null,
    created_at timestamp with time zone not null,
    constraint uq_validation_runs_case_run unique (case_id, run_no),
    constraint fk_validation_runs_case foreign key (case_id) references cases(id)
);

create index idx_validation_runs_case on validation_runs(case_id);

create table validation_issues (
    id uuid primary key,
    validation_run_id uuid not null,
    code varchar(64) not null,
    rule_id varchar(64) not null,
    severity varchar(16) not null,
    message varchar(2000) not null,
    constraint fk_validation_issues_run foreign key (validation_run_id) references validation_runs(id)
);

create index idx_validation_issues_run on validation_issues(validation_run_id);

