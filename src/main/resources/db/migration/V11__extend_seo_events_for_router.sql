alter table seo_events
    add column query_text varchar(255);

alter table seo_events
    add column match_target varchar(255);

create index idx_seo_events_query_text on seo_events (query_text);
