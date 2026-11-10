-- V9__movie_slug_unique_among_active.sql
-- Slug phim chỉ cần duy nhất giữa các phim chưa xóa mềm

ALTER TABLE movies DROP CONSTRAINT uq_movies_slug;

CREATE UNIQUE INDEX uq_movies_active_slug ON movies (slug) WHERE NOT is_deleted;
