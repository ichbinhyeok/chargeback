create table seo_events (
    id uuid primary key,
    event_name varchar(64) not null,
    platform_slug varchar(32),
    guide_slug varchar(128),
    page_path varchar(255) not null,
    source_channel varchar(32),
    session_id varchar(64) not null,
    referrer varchar(512),
    user_agent varchar(512),
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index idx_seo_events_occurred_at on seo_events (occurred_at);
create index idx_seo_events_event_name on seo_events (event_name);
create index idx_seo_events_page_path on seo_events (page_path);
