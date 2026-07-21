-- Many-to-many association: which course_chunks a flashcard was generated from.
CREATE TABLE flashcard_sources (
    flashcard_id  UUID NOT NULL REFERENCES flashcards (id),
    chunk_id      UUID NOT NULL REFERENCES course_chunks (id),
    PRIMARY KEY (flashcard_id, chunk_id)
);

CREATE INDEX idx_flashcard_sources_chunk_id ON flashcard_sources (chunk_id);
