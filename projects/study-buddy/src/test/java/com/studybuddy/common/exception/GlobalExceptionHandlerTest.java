package com.studybuddy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Verifies that every exception this application throws maps to a
 * ProblemDetail with a *consistent schema*: a real HTTP status and a
 * non-blank "detail" message, with "fieldErrors" present only (and always)
 * on validation failures. A client should never have to special-case how it
 * reads an error response depending on which endpoint produced it.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertConsistentSchema(ProblemDetail problem, HttpStatus expectedStatus) {
        assertThat(problem.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problem.getDetail()).isNotBlank();
    }

    @Test
    void resourceNotFoundMapsTo404WithConsistentSchema() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("not found"));
        assertConsistentSchema(problem, HttpStatus.NOT_FOUND);
    }

    @Test
    void unsupportedFileTypeMapsTo415WithConsistentSchema() {
        ProblemDetail problem = handler.handleUnsupportedFileType(new UnsupportedFileTypeException("bad type"));
        assertConsistentSchema(problem, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void documentProcessingMapsTo422WithConsistentSchema() {
        ProblemDetail problem = handler.handleDocumentProcessing(new DocumentProcessingException("bad document"));
        assertConsistentSchema(problem, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void maxUploadSizeExceededMapsTo413WithConsistentSchema() {
        ProblemDetail problem = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(1024));
        assertConsistentSchema(problem, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void tutorAnswerTimeoutMapsTo504WithConsistentSchema() {
        ProblemDetail problem = handler.handleTutorAnswerTimeout(
                new TutorAnswerTimeoutException("timed out", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void tutorAnswerGenerationMapsTo502WithConsistentSchema() {
        ProblemDetail problem = handler.handleTutorAnswerGeneration(
                new TutorAnswerGenerationException("failed", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.BAD_GATEWAY);
    }

    @Test
    void noRelevantContextMapsTo404WithConsistentSchema() {
        ProblemDetail problem = handler.handleNoRelevantContext(new NoRelevantContextException("no context"));
        assertConsistentSchema(problem, HttpStatus.NOT_FOUND);
    }

    @Test
    void flashcardGenerationTimeoutMapsTo504WithConsistentSchema() {
        ProblemDetail problem = handler.handleFlashcardGenerationTimeout(
                new FlashcardGenerationTimeoutException("timed out", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void flashcardGenerationMapsTo502WithConsistentSchema() {
        ProblemDetail problem = handler.handleFlashcardGeneration(
                new FlashcardGenerationException("failed", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.BAD_GATEWAY);
    }

    @Test
    void quizGenerationTimeoutMapsTo504WithConsistentSchema() {
        ProblemDetail problem = handler.handleQuizGenerationTimeout(
                new QuizGenerationTimeoutException("timed out", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void quizGenerationMapsTo502WithConsistentSchema() {
        ProblemDetail problem = handler.handleQuizGeneration(
                new QuizGenerationException("failed", new RuntimeException()));
        assertConsistentSchema(problem, HttpStatus.BAD_GATEWAY);
    }

    @Test
    void quizSubmissionMapsTo400WithConsistentSchema() {
        ProblemDetail problem = handler.handleQuizSubmission(new QuizSubmissionException("bad submission"));
        assertConsistentSchema(problem, HttpStatus.BAD_REQUEST);
    }

    @Test
    void unsupportedContentTypeOnAMultipartEndpointMapsTo415NotInternalServerError() {
        // Real bug found via live testing: POSTing to a multipart-only endpoint
        // (e.g. /api/audio/transcribe, /api/documents/upload) with no body/wrong
        // Content-Type throws HttpMediaTypeNotSupportedException from Spring's
        // own handler mapping, before the controller or any validation runs.
        // Left unhandled, it fell through to the generic 500 handler.
        ProblemDetail problem = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException("application/x-www-form-urlencoded", List.of(MediaType.MULTIPART_FORM_DATA)));
        assertConsistentSchema(problem, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void apiKeyValidationFailureMapsTo422WithConsistentSchema() {
        ProblemDetail problem = handler.handleApiKeyValidation(
                new ApiKeyValidationException("Anthropic rejected this key"));
        assertConsistentSchema(problem, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unreadableMessageMapsTo400WithConsistentSchema() {
        ProblemDetail problem = handler.handleUnreadableMessage(new HttpMessageNotReadableException("bad json"));
        assertConsistentSchema(problem, HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingStaticResourceMapsTo404NotInternalServerError() {
        // Real-world case: browsers auto-request /favicon.ico on every page load.
        // Left unhandled, NoResourceFoundException fell through to the generic
        // 500 handler and got logged as an ERROR-level "unhandled exception" for
        // routine, expected browser behavior — this should be a quiet 404.
        ProblemDetail problem = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "favicon.ico"));
        assertConsistentSchema(problem, HttpStatus.NOT_FOUND);
    }

    @Test
    void unexpectedExceptionMapsTo500WithConsistentSchemaAndNoLeakedDetails() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("sensitive stack trace info"));
        assertConsistentSchema(problem, HttpStatus.INTERNAL_SERVER_ERROR);
        // Generic failures must not leak internal exception messages to the client.
        assertThat(problem.getDetail()).doesNotContain("sensitive stack trace info");
    }

    @Test
    void validationFailureMapsTo400AndIncludesFieldErrorsConsistently() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "flashcardGenerateRequest");
        bindingResult.addError(new FieldError("flashcardGenerateRequest", "topic", "must not be blank"));
        bindingResult.addError(new FieldError("flashcardGenerateRequest", "count", "must be at most 20"));
        Method dummyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyHandlerMethod", String.class);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(dummyMethod, 0), bindingResult);

        ProblemDetail problem = handler.handleValidation(ex);

        assertConsistentSchema(problem, HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) problem.getProperties().get("fieldErrors");
        assertThat(fieldErrors)
                .containsEntry("topic", "must not be blank")
                .containsEntry("count", "must be at most 20");
    }

    @Test
    void everyHandledExceptionTypeProducesNonNullStatusAndDetail() {
        List<ProblemDetail> allResponses = List.of(
                handler.handleNotFound(new ResourceNotFoundException("x")),
                handler.handleUnsupportedFileType(new UnsupportedFileTypeException("x")),
                handler.handleDocumentProcessing(new DocumentProcessingException("x")),
                handler.handleTutorAnswerTimeout(new TutorAnswerTimeoutException("x", new RuntimeException())),
                handler.handleTutorAnswerGeneration(new TutorAnswerGenerationException("x", new RuntimeException())),
                handler.handleNoRelevantContext(new NoRelevantContextException("x")),
                handler.handleFlashcardGenerationTimeout(new FlashcardGenerationTimeoutException("x", new RuntimeException())),
                handler.handleFlashcardGeneration(new FlashcardGenerationException("x", new RuntimeException())),
                handler.handleQuizGenerationTimeout(new QuizGenerationTimeoutException("x", new RuntimeException())),
                handler.handleQuizGeneration(new QuizGenerationException("x", new RuntimeException())),
                handler.handleQuizSubmission(new QuizSubmissionException("x")),
                handler.handleUnexpected(new RuntimeException("x")));

        assertThat(allResponses).allSatisfy(problem -> {
            assertThat(problem.getStatus()).isPositive();
            assertThat(problem.getDetail()).isNotBlank();
        });
    }

    @SuppressWarnings("unused")
    private void dummyHandlerMethod(String value) {
    }
}
