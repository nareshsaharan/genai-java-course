-- Links each chunk back to the document it was extracted from.
ALTER TABLE course_chunks
    ADD COLUMN document_id UUID REFERENCES documents (id);

CREATE INDEX idx_course_chunks_document_id ON course_chunks (document_id);
