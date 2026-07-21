-- Stores every embedded chunk produced by the document ingestion pipeline:
-- the chunk text, its 384-dim embedding (all-MiniLM-L6-v2), and enough
-- metadata to cite the source when a tutor chat answer is grounded on it.
CREATE TABLE course_chunks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content      TEXT NOT NULL,
    embedding    VECTOR(384) NOT NULL,
    source_file  TEXT NOT NULL,
    topic        TEXT,
    chunk_index  INTEGER NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Approximate nearest-neighbor index for cosine similarity search, the
-- distance metric used by LangChain4j's PgVectorEmbeddingStore.
CREATE INDEX idx_course_chunks_embedding_cosine
    ON course_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Supports "list chunks for this file" / "filter retrieval by topic" lookups.
CREATE INDEX idx_course_chunks_source_file ON course_chunks (source_file);
CREATE INDEX idx_course_chunks_topic ON course_chunks (topic);
