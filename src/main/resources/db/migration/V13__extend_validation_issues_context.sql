alter table validation_issues
    add column target_scope varchar(32);

alter table validation_issues
    add column target_evidence_type varchar(64);

alter table validation_issues
    add column target_file_id varchar(64);

alter table validation_issues
    add column target_group_key varchar(128);

alter table validation_issues
    add column fix_strategy varchar(64);
