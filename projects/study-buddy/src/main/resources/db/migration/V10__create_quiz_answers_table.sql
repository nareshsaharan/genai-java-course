CREATE TABLE quiz_answers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id              UUID NOT NULL REFERENCES quiz_attempts (id),
    question_id             UUID NOT NULL REFERENCES quiz_questions (id),
    selected_option_index   INTEGER NOT NULL,
    is_correct              BOOLEAN NOT NULL
);

CREATE INDEX idx_quiz_answers_attempt_id ON quiz_answers (attempt_id);
