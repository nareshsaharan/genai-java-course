package com.studybuddy.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central mapping from exceptions to RFC 7807 ProblemDetail responses.
 * Each Part 1 / Part 2 feature module throws the exceptions below rather
 * than building its own error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ProblemDetail handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ProblemDetail handleDocumentProcessing(DocumentProcessingException ex) {
        log.error("Document processing failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler(TutorAnswerTimeoutException.class)
    public ProblemDetail handleTutorAnswerTimeout(TutorAnswerTimeoutException ex) {
        log.error("Tutor model call timed out", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(TutorAnswerGenerationException.class)
    public ProblemDetail handleTutorAnswerGeneration(TutorAnswerGenerationException ex) {
        log.error("Tutor model call failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(NoRelevantContextException.class)
    public ProblemDetail handleNoRelevantContext(NoRelevantContextException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(FlashcardGenerationTimeoutException.class)
    public ProblemDetail handleFlashcardGenerationTimeout(FlashcardGenerationTimeoutException ex) {
        log.error("Flashcard model call timed out", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(FlashcardGenerationException.class)
    public ProblemDetail handleFlashcardGeneration(FlashcardGenerationException ex) {
        log.error("Flashcard model call failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(QuizGenerationTimeoutException.class)
    public ProblemDetail handleQuizGenerationTimeout(QuizGenerationTimeoutException ex) {
        log.error("Quiz model call timed out", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(QuizGenerationException.class)
    public ProblemDetail handleQuizGeneration(QuizGenerationException ex) {
        log.error("Quiz model call failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(QuizSubmissionException.class)
    public ProblemDetail handleQuizSubmission(QuizSubmissionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AudioProcessingException.class)
    public ProblemDetail handleAudioProcessing(AudioProcessingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedAudioFormatException.class)
    public ProblemDetail handleUnsupportedAudioFormat(UnsupportedAudioFormatException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(AudioTooLargeException.class)
    public ProblemDetail handleAudioTooLarge(AudioTooLargeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
    }

    @ExceptionHandler(AudioTranscriptionTimeoutException.class)
    public ProblemDetail handleAudioTranscriptionTimeout(AudioTranscriptionTimeoutException ex) {
        log.error("Whisper transcription call timed out", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(AudioTranscriptionException.class)
    public ProblemDetail handleAudioTranscription(AudioTranscriptionException ex) {
        log.error("Whisper transcription call failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(ApiKeyValidationException.class)
    public ProblemDetail handleApiKeyValidation(ApiKeyValidationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        // Thrown by Spring's own handler mapping — e.g. a multipart-only
        // endpoint (/api/audio/transcribe, /api/documents/upload) hit with
        // no body or the wrong Content-Type — before any controller or
        // validation code runs. Without this handler it fell through to the
        // generic 500 handler below, which is wrong: this is a client error.
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Malformed or missing multipart request body");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        // Routine — e.g. every browser auto-requests /favicon.ico. Left
        // unhandled, this fell through to the generic 500 handler and got
        // logged as an ERROR-level "unhandled exception" for completely
        // expected behavior. No log.error here: a missing static asset isn't
        // an application error.
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such resource: " + ex.getResourcePath());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
