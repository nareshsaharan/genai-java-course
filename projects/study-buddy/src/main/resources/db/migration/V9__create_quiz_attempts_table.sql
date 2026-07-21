CREATE TABLE quiz_attempts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id        UUID NOT NULL REFERENCES quizzes (id),
    topic          TEXT NOT NULL,
    correct_count  INTEGER NOT NULL,
    total_count    INTEGER NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quiz_attempts_quiz_id ON quiz_attempts (quiz_id);
CREATE INDEX idx_quiz_attempts_topic ON quiz_attempts (topic);
