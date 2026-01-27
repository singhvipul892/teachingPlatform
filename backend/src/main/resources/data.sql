insert into videos (video_id, title, section, thumbnail_url, duration, display_order)
values
  ('a1b2c3d4', 'Introduction to Algebra', 'Algebra', 'https://img.youtube.com/vi/a1b2c3d4/hqdefault.jpg', '12:45', 1),
  ('e5f6g7h8', 'Linear Equations Basics', 'Algebra', 'https://img.youtube.com/vi/e5f6g7h8/hqdefault.jpg', '09:20', 2),
  ('i9j0k1l2', 'Trigonometry Fundamentals', 'Trigonometry', 'https://img.youtube.com/vi/i9j0k1l2/hqdefault.jpg', '14:10', 1),
  ('m3n4o5p6', 'Triangles and Angles', 'Geometry', 'https://img.youtube.com/vi/m3n4o5p6/hqdefault.jpg', '11:05', 1);

insert into video_pdfs (video_id_fk, title, pdf_type, file_url, display_order)
values
  (1, 'Algebra Notes - Part 1', 'Notes', 's3://example-bucket/algebra/notes-1.pdf', 1),
  (1, 'Algebra Practice Sheet', 'Practice Questions', 's3://example-bucket/algebra/practice-1.pdf', 2),
  (3, 'Trigonometry Formula Sheet', 'Formula Sheet', 's3://example-bucket/trigonometry/formulas.pdf', 1);
