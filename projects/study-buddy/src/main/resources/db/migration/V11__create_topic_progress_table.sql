-- One row per topic. accuracy is a recency-weighted score (not a plain
-- correct_count/total_count ratio) — see TopicProgressCalculator.
-- last_recommended_at supports rotating between near-tied weak topics
-- instead of always recommending the single lowest scorer.
CREATE TABLE topic_progress (
    topic                 TEXT PRIMARY KEY,
    correct_count         INTEGER NOT NULL DEFAULT 0,
    total_count           INTEGER NOT NULL DEFAULT 0,
    accuracy              DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_attempt_at       TIMESTAMPTZ,
    last_recommended_at   TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
