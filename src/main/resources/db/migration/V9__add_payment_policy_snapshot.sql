alter table payments add column policy_version varchar(64);
alter table payments add column required_evidence_snapshot varchar(1024);
