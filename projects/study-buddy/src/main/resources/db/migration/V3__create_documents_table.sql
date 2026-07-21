-- One row per successfully ingested source document. content_hash is a
-- SHA-256 of the extracted text, used to reject duplicate ingestion of the
-- same content (even under a different filename).
CREATE TABLE documents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_filename  TEXT NOT NULL,
    topic            TEXT,
    content_hash     TEXT NOT NULL,
    chunk_count      INTEGER NOT NULL DEFAULT 0,
    status           TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_documents_content_hash UNIQUE (content_hash)
);
