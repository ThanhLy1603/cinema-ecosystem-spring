ALTER TABLE movies DROP CONSTRAINT chk_movies_status;

ALTER TABLE movies
    ADD CONSTRAINT chk_movies_status CHECK (status IN ('DRAFT','COMING_SOON','NOW_SHOWING','ENDED'));
