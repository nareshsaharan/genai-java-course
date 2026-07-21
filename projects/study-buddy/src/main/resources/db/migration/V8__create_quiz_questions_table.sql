-- options is a JSON array of answer-choice strings (e.g. ["A", "B", "C", "D"]).
-- source_chunk_id traces the question back to the retrieved chunk it was
-- grounded in; nullable because a chunk could later be deleted independently.
CREATE TABLE quiz_questions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id               UUID NOT NULL REFERENCES quizzes (id),
    question_index        INTEGER NOT NULL,
    question_text         TEXT NOT NULL,
    options               JSONB NOT NULL,
    correct_option_index  INTEGER NOT NULL,
    source_chunk_id       UUID REFERENCES course_chunks (id),
    UNIQUE (quiz_id, question_index)
);

CREATE INDEX idx_quiz_questions_quiz_id ON quiz_questions (quiz_id);
