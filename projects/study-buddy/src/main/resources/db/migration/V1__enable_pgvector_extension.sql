-- Enables the pgvector extension so VECTOR(n) columns and vector similarity
-- operators (<->, <#>, <=>) are available. Requires the pgvector/pgvector
-- Docker image (or an equivalent Postgres build with pgvector installed).
CREATE EXTENSION IF NOT EXISTS vector;
