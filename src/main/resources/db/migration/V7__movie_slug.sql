-- V7__movie_slug.sql
-- Đường dẫn thân thiện SEO cho phim: /movies/<slug> thay cho /movies/<id>

ALTER TABLE movies ADD COLUMN slug VARCHAR(255);

WITH base_slugs AS (
    SELECT
        movie.id,
        COALESCE(
            NULLIF(
                trim(both '-' from regexp_replace(
                    lower(replace(replace(regexp_replace(normalize(movie.title, NFD), '[̀-ͯ]', '', 'g'), 'đ', 'd'), 'Đ', 'D')),
                    '[^a-z0-9]+', '-', 'g')),
                ''),
            'phim') AS base_slug
    FROM movies movie
),
ranked_slugs AS (
    SELECT
        base_slugs.id,
        left(base_slugs.base_slug, 200) AS base_slug,
        row_number() OVER (PARTITION BY left(base_slugs.base_slug, 200) ORDER BY base_slugs.id) AS slug_rank
    FROM base_slugs
)
UPDATE movies movie
SET slug = CASE WHEN ranked_slugs.slug_rank = 1
                THEN trim(both '-' from ranked_slugs.base_slug)
                ELSE trim(both '-' from ranked_slugs.base_slug) || '-' || movie.id END
FROM ranked_slugs
WHERE ranked_slugs.id = movie.id;

ALTER TABLE movies
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT uq_movies_slug UNIQUE (slug),
    ADD CONSTRAINT chk_movies_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$');
