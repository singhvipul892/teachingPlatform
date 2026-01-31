create table if not exists videos (
    id bigserial primary key,
    video_id varchar(64) not null,
    title varchar(200) not null,
    section varchar(80) not null,
    thumbnail_url varchar(500) not null,
    duration varchar(20) not null,
    display_order integer not null
);

create index if not exists idx_videos_section on videos (section);
create index if not exists idx_videos_section_order on videos (section, display_order);

create table video_pdfs (
  id bigserial primary key,
  video_id_fk bigint not null references videos(id) on delete cascade,
  title varchar(200) not null,
  pdf_type varchar(100) not null,
  file_url varchar(500) not null,
  display_order integer not null,
  created_at timestamptz not null default now()
);

create index idx_video_pdfs_video_id on video_pdfs(video_id_fk);


