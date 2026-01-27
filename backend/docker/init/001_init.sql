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

insert into videos (video_id, title, section, thumbnail_url, duration, display_order)
values
  ('a1b2c3d4', 'Introduction to Algebra', 'Algebra', 'https://img.youtube.com/vi/a1b2c3d4/hqdefault.jpg', '12:45', 1),
  ('e5f6g7h8', 'Linear Equations Basics', 'Algebra', 'https://img.youtube.com/vi/e5f6g7h8/hqdefault.jpg', '09:20', 2),
  ('i9j0k1l2', 'Trigonometry Fundamentals', 'Trigonometry', 'https://img.youtube.com/vi/i9j0k1l2/hqdefault.jpg', '14:10', 1),
  ('m3n4o5p6', 'Triangles and Angles', 'Geometry', 'https://img.youtube.com/vi/m3n4o5p6/hqdefault.jpg', '11:05', 1)
on conflict do nothing;

insert into video_pdfs (video_id_fk, title, pdf_type, file_url, display_order)
values
  (1, 'Algebra Notes - Part 1', 'Notes', 's3://example-bucket/algebra/notes-1.pdf', 1),
  (1, 'Algebra Practice Sheet', 'Practice Questions', 's3://example-bucket/algebra/practice-1.pdf', 2),
  (3, 'Trigonometry Formula Sheet', 'Formula Sheet', 's3://example-bucket/trigonometry/formulas.pdf', 1)
on conflict do nothing;
