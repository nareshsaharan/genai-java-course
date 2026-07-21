package com.studybuddy.document;

/** Outcome of a single document upload. */
public enum IngestionStatus {

    /** Text was extracted, chunked, embedded and stored. */
    INGESTED,

    /** A document with the same content hash was already ingested; nothing new was stored. */
    DUPLICATE
}
