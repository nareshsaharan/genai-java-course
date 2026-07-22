'use strict';

/* =========================================================================
 * Study Buddy frontend — vanilla JS, no framework.
 *
 * Organization:
 *   - DOM helpers   : safe element construction (no innerHTML with
 *                      untrusted content — everything goes through
 *                      textContent or element.setAttribute).
 *   - API layer     : one function per backend endpoint, fetch + async/await.
 *   - UI modules    : one init function per section, wiring forms to the
 *                      API layer and rendering results with the DOM helpers.
 * ========================================================================= */

/* ---------------------------------------------------------------------
 * DOM helpers
 * ------------------------------------------------------------------- */

/**
 * Creates an element safely. `text`, if provided, is set via textContent
 * (never innerHTML), so it can never be interpreted as markup — this is
 * how all model-generated / user-generated content reaches the DOM.
 */
function createElement(tagName, { className, text, attrs } = {}) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (text !== undefined && text !== null) {
    element.textContent = text;
  }
  if (attrs) {
    for (const [name, value] of Object.entries(attrs)) {
      element.setAttribute(name, value);
    }
  }
  return element;
}

function clearChildren(node) {
  while (node.firstChild) {
    node.removeChild(node.firstChild);
  }
}

function show(node) {
  node.hidden = false;
}

function hide(node) {
  node.hidden = true;
}

function setStatus(node, { text, variant }) {
  node.textContent = text;
  node.className = variant ? `status-region status-${variant}` : 'status-region';
  show(node);
}

function clearStatus(node) {
  node.textContent = '';
  node.className = 'status-region';
  hide(node);
}

/* ---------------------------------------------------------------------
 * API layer
 * ------------------------------------------------------------------- */

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Reads the backend's RFC 7807 ProblemDetail error body (if present) and
 * returns a human-readable message; falls back to a generic message for
 * network errors or unexpected response shapes.
 */
async function readErrorMessage(response) {
  try {
    const body = await response.json();
    if (body && typeof body.detail === 'string' && body.detail.length > 0) {
      return body.detail;
    }
  } catch (parseError) {
    // response body wasn't JSON (or was empty) — fall through to default message
  }
  return `Request failed with status ${response.status}`;
}

async function apiUploadDocument(file, topic) {
  const formData = new FormData();
  formData.append('file', file);
  if (topic) {
    formData.append('topic', topic);
  }

  const response = await fetch('/api/documents/upload', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiTutorChat(question, topic) {
  const response = await fetch('/api/tutor/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, topic: topic || null }),
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiTranscribeAudio(audioBlob, filename) {
  const formData = new FormData();
  formData.append('file', audioBlob, filename);

  const response = await fetch('/api/audio/transcribe', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiGenerateFlashcards(topic, count, difficulty) {
  const response = await fetch('/api/flashcards', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic, count, difficulty }),
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiGenerateQuiz(topic, count, difficulty) {
  const response = await fetch('/api/quizzes/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic, count, difficulty }),
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiSubmitQuiz(quizId, answers) {
  const response = await fetch(`/api/quizzes/${encodeURIComponent(quizId)}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answers }),
  });

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiGetTopicProgress() {
  const response = await fetch('/api/progress/topics');
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

/**
 * Returns `null` (not an error) on 404 — that status means "no recommendation
 * available right now" (no quiz history yet, or nothing currently weak),
 * which is a normal state for this UI to render quietly, not a failure.
 */
async function apiGetRecommendation() {
  const response = await fetch('/api/progress/recommendation');
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiGetSettingsStatus() {
  const response = await fetch('/api/settings/keys');
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiSaveKey(provider, apiKey) {
  const response = await fetch(`/api/settings/keys/${provider}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey }),
  });
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiClearKey(provider) {
  const response = await fetch(`/api/settings/keys/${provider}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiSelectProvider(kind, provider) {
  const response = await fetch(`/api/settings/${kind}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider }),
  });
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

/* ---------------------------------------------------------------------
 * UI: 1. Document upload
 * ------------------------------------------------------------------- */

function initUploadSection() {
  const form = document.getElementById('upload-form');
  const fileInput = document.getElementById('upload-file');
  const topicInput = document.getElementById('upload-topic');
  const submitButton = document.getElementById('upload-button');
  const fieldError = document.getElementById('upload-field-error');
  const statusRegion = document.getElementById('upload-status');

  const ALLOWED_EXTENSIONS = ['.pdf', '.txt', '.md', '.markdown'];

  function validate() {
    const file = fileInput.files[0];
    if (!file) {
      return 'Please choose a file to upload.';
    }
    const lowerName = file.name.toLowerCase();
    const hasAllowedExtension = ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
    if (!hasAllowedExtension) {
      return 'Only PDF, TXT and Markdown files are supported.';
    }
    return null;
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    fieldError.textContent = '';
    clearStatus(statusRegion);

    const validationError = validate();
    if (validationError) {
      fieldError.textContent = validationError;
      return;
    }

    const file = fileInput.files[0];
    const topic = topicInput.value.trim();

    submitButton.disabled = true;
    setStatus(statusRegion, { text: 'Uploading and processing document…', variant: undefined });

    try {
      const result = await apiUploadDocument(file, topic);
      const statusWord = result.status === 'DUPLICATE'
        ? 'This document was already ingested previously'
        : 'Uploaded successfully';
      setStatus(statusRegion, {
        text: `${statusWord}: "${result.sourceFilename}" (${result.chunkCount} chunk${result.chunkCount === 1 ? '' : 's'}).`,
        variant: result.status === 'DUPLICATE' ? 'warn' : 'success',
      });
      form.reset();
    } catch (error) {
      setStatus(statusRegion, {
        text: error instanceof ApiError ? error.message : 'Upload failed. Please try again.',
        variant: 'error',
      });
    } finally {
      submitButton.disabled = false;
    }
  });
}

/* ---------------------------------------------------------------------
 * UI: 2. RAG Tutor
 * ------------------------------------------------------------------- */

function initTutorSection() {
  const form = document.getElementById('tutor-form');
  const questionInput = document.getElementById('tutor-question');
  const topicInput = document.getElementById('tutor-topic');
  const submitButton = document.getElementById('tutor-button');
  const fieldError = document.getElementById('tutor-field-error');
  const loadingRegion = document.getElementById('tutor-loading');
  const errorRegion = document.getElementById('tutor-error');
  const resultRegion = document.getElementById('tutor-result');
  const confidenceBadge = document.getElementById('tutor-confidence');
  const answerText = document.getElementById('tutor-answer');
  const sourcesBlock = document.getElementById('tutor-sources');
  const sourcesSummary = document.getElementById('tutor-sources-summary');
  const sourcesList = document.getElementById('tutor-sources-list');

  function renderSources(sources) {
    clearChildren(sourcesList);

    if (!sources || sources.length === 0) {
      hide(sourcesBlock);
      return;
    }

    sourcesSummary.textContent = `Sources (${sources.length})`;

    for (const source of sources) {
      const item = createElement('li', { className: 'source-item' });

      const header = createElement('div', { className: 'source-item-header' });
      header.appendChild(createElement('span', { text: `${source.sourceFile} — chunk ${source.chunkIndex}` }));
      header.appendChild(createElement('span', {
        className: 'source-score',
        text: `similarity ${source.similarityScore.toFixed(2)}`,
      }));
      item.appendChild(header);

      item.appendChild(createElement('p', { className: 'source-snippet', text: source.snippet }));

      sourcesList.appendChild(item);
    }

    show(sourcesBlock);
  }

  function renderResult(result) {
    confidenceBadge.textContent = result.confidence.replace(/_/g, ' ');
    confidenceBadge.className = `confidence-badge confidence-${result.confidence.toLowerCase()}`;
    answerText.textContent = result.answer;
    renderSources(result.sources);
    show(resultRegion);
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    fieldError.textContent = '';
    hide(errorRegion);
    hide(resultRegion);

    const question = questionInput.value.trim();
    const topic = topicInput.value.trim();

    if (!question) {
      fieldError.textContent = 'Please enter a question.';
      return;
    }

    submitButton.disabled = true;
    show(loadingRegion);

    try {
      const result = await apiTutorChat(question, topic);
      renderResult(result);
    } catch (error) {
      errorRegion.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while asking the tutor. Please try again.';
      show(errorRegion);
    } finally {
      hide(loadingRegion);
      submitButton.disabled = false;
    }
  });
}

/* ---------------------------------------------------------------------
 * UI: 2b. Optional voice input (feeds the tutor question textarea)
 * ------------------------------------------------------------------- */

/**
 * Records a voice question via MediaRecorder, lets the student review or
 * discard it, then uploads it for transcription and inserts the result into
 * `questionTextarea` — it never submits the tutor form itself; the student
 * always reviews the transcript and clicks "Ask" themselves.
 */
function initVoiceInputSection(questionTextarea) {
  const recordButton = document.getElementById('voice-record-button');
  const recordingState = document.getElementById('voice-recording-state');
  const stopButton = document.getElementById('voice-stop-button');
  const timerLabel = document.getElementById('voice-timer');
  const reviewState = document.getElementById('voice-review-state');
  const reviewPlayer = document.getElementById('voice-review-player');
  const useButton = document.getElementById('voice-use-button');
  const discardButton = document.getElementById('voice-discard-button');
  const uploadingState = document.getElementById('voice-uploading-state');
  const errorRegion = document.getElementById('voice-error');

  const CANDIDATE_MIME_TYPES = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/mp4',
    'audio/ogg;codecs=opus',
  ];

  const state = {
    mediaRecorder: null,
    mediaStream: null,
    chunks: [],
    recordedBlob: null,
    objectUrl: null,
    timerIntervalId: null,
    recordingStartedAt: null,
  };

  if (typeof window.MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
    recordButton.disabled = true;
    recordButton.title = 'Voice recording is not supported in this browser.';
    return;
  }

  function pickSupportedMimeType() {
    return CANDIDATE_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type)) || '';
  }

  function resetToIdle() {
    hide(recordingState);
    hide(reviewState);
    hide(uploadingState);
    show(recordButton);
    recordButton.disabled = false;

    if (state.objectUrl) {
      URL.revokeObjectURL(state.objectUrl);
    }
    state.objectUrl = null;
    state.recordedBlob = null;
    state.chunks = [];
    reviewPlayer.removeAttribute('src');

    if (state.mediaStream) {
      state.mediaStream.getTracks().forEach((track) => track.stop());
      state.mediaStream = null;
    }
  }

  function formatElapsed(startedAt) {
    const totalSeconds = Math.floor((Date.now() - startedAt) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  }

  async function startRecording() {
    errorRegion.textContent = '';

    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      errorRegion.textContent = 'Microphone access was denied or is unavailable.';
      return;
    }

    state.mediaStream = stream;
    state.chunks = [];

    const mimeType = pickSupportedMimeType();
    state.mediaRecorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);

    state.mediaRecorder.addEventListener('dataavailable', (event) => {
      if (event.data && event.data.size > 0) {
        state.chunks.push(event.data);
      }
    });

    state.mediaRecorder.addEventListener('stop', () => {
      const blobType = state.mediaRecorder.mimeType || 'audio/webm';
      state.recordedBlob = new Blob(state.chunks, { type: blobType });
      state.objectUrl = URL.createObjectURL(state.recordedBlob);
      reviewPlayer.src = state.objectUrl;

      hide(recordingState);
      show(reviewState);
      clearInterval(state.timerIntervalId);
    });

    state.mediaRecorder.start();
    state.recordingStartedAt = Date.now();
    timerLabel.textContent = '0:00';
    state.timerIntervalId = setInterval(() => {
      timerLabel.textContent = formatElapsed(state.recordingStartedAt);
    }, 500);

    hide(recordButton);
    show(recordingState);
  }

  function stopRecording() {
    if (state.mediaRecorder && state.mediaRecorder.state !== 'inactive') {
      state.mediaRecorder.stop();
    }
  }

  function discardRecording() {
    resetToIdle();
  }

  async function useRecording() {
    if (!state.recordedBlob) {
      return;
    }

    hide(reviewState);
    show(uploadingState);
    useButton.disabled = true;
    discardButton.disabled = true;

    const extension = (state.recordedBlob.type.split('/')[1] || 'webm').split(';')[0];

    try {
      const result = await apiTranscribeAudio(state.recordedBlob, `voice-question.${extension}`);
      const existing = questionTextarea.value.trim();
      questionTextarea.value = existing ? `${existing} ${result.transcript}` : result.transcript;
      questionTextarea.focus();
      resetToIdle();
    } catch (error) {
      errorRegion.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while transcribing. Please try again.';
      hide(uploadingState);
      show(reviewState);
    } finally {
      useButton.disabled = false;
      discardButton.disabled = false;
    }
  }

  recordButton.addEventListener('click', startRecording);
  stopButton.addEventListener('click', stopRecording);
  discardButton.addEventListener('click', discardRecording);
  useButton.addEventListener('click', useRecording);
}

/* ---------------------------------------------------------------------
 * UI: 3. Flashcard generator
 * ------------------------------------------------------------------- */

function initFlashcardSection() {
  const form = document.getElementById('flashcard-form');
  const topicInput = document.getElementById('flashcard-topic');
  const countInput = document.getElementById('flashcard-count');
  const difficultySelect = document.getElementById('flashcard-difficulty');
  const submitButton = document.getElementById('flashcard-button');
  const fieldError = document.getElementById('flashcard-field-error');
  const loadingRegion = document.getElementById('flashcard-loading');
  const errorRegion = document.getElementById('flashcard-error');
  const viewer = document.getElementById('flashcard-viewer');
  const card = document.getElementById('flashcard-card');
  const questionText = document.getElementById('flashcard-question-text');
  const answerText = document.getElementById('flashcard-answer-text');
  const prevButton = document.getElementById('flashcard-prev');
  const nextButton = document.getElementById('flashcard-next');
  const positionLabel = document.getElementById('flashcard-position');

  const state = { cards: [], currentIndex: 0 };

  function validate() {
    if (!topicInput.value.trim()) {
      return 'Please enter a topic.';
    }
    const count = Number(countInput.value);
    if (!Number.isInteger(count) || count < 1 || count > 20) {
      return 'Number of cards must be a whole number between 1 and 20.';
    }
    if (!difficultySelect.value) {
      return 'Please choose a difficulty.';
    }
    return null;
  }

  function setFlipped(flipped) {
    card.setAttribute('aria-pressed', String(flipped));
    const current = state.cards[state.currentIndex];
    card.setAttribute(
      'aria-label',
      flipped ? `Answer: ${current.answer}. Press to flip back to the question.`
              : `Question: ${current.question}. Press to reveal the answer.`
    );
  }

  function renderCurrentCard() {
    const total = state.cards.length;
    const current = state.cards[state.currentIndex];

    questionText.textContent = current.question;
    answerText.textContent = current.answer;
    setFlipped(false);

    positionLabel.textContent = `Card ${state.currentIndex + 1} of ${total}`;
    prevButton.disabled = state.currentIndex === 0;
    nextButton.disabled = state.currentIndex === total - 1;
  }

  card.addEventListener('click', () => {
    setFlipped(card.getAttribute('aria-pressed') !== 'true');
  });

  prevButton.addEventListener('click', () => {
    if (state.currentIndex > 0) {
      state.currentIndex -= 1;
      renderCurrentCard();
    }
  });

  nextButton.addEventListener('click', () => {
    if (state.currentIndex < state.cards.length - 1) {
      state.currentIndex += 1;
      renderCurrentCard();
    }
  });

  // Left/Right arrow keys move between cards when focus is anywhere in the viewer.
  viewer.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowLeft' && !prevButton.disabled) {
      event.preventDefault();
      prevButton.click();
    } else if (event.key === 'ArrowRight' && !nextButton.disabled) {
      event.preventDefault();
      nextButton.click();
    }
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    fieldError.textContent = '';
    hide(errorRegion);
    hide(viewer);

    const validationError = validate();
    if (validationError) {
      fieldError.textContent = validationError;
      return;
    }

    const topic = topicInput.value.trim();
    const count = Number(countInput.value);
    const difficulty = difficultySelect.value;

    submitButton.disabled = true;
    show(loadingRegion);

    try {
      const result = await apiGenerateFlashcards(topic, count, difficulty);
      if (!result.cards || result.cards.length === 0) {
        errorRegion.textContent = 'No flashcards could be generated for this topic.';
        show(errorRegion);
        return;
      }
      state.cards = result.cards;
      state.currentIndex = 0;
      renderCurrentCard();
      show(viewer);
    } catch (error) {
      errorRegion.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while generating flashcards. Please try again.';
      show(errorRegion);
    } finally {
      hide(loadingRegion);
      submitButton.disabled = false;
    }
  });
}

/* ---------------------------------------------------------------------
 * UI: 4. Progress / weak topics
 * ------------------------------------------------------------------- */

function initProgressSection() {
  const refreshButton = document.getElementById('progress-refresh-button');
  const loadingRegion = document.getElementById('progress-loading');
  const errorRegion = document.getElementById('progress-error');
  const contentRegion = document.getElementById('progress-content');
  const recommendationBlock = document.getElementById('progress-recommendation');
  const topicsBody = document.getElementById('progress-topics-body');
  const emptyMessage = document.getElementById('progress-empty');

  function renderRecommendation(recommendation) {
    if (!recommendation) {
      hide(recommendationBlock);
      return;
    }
    clearChildren(recommendationBlock);
    recommendationBlock.appendChild(createElement('strong', { text: `Recommended next: ${recommendation.topic}. ` }));
    recommendationBlock.appendChild(document.createTextNode(recommendation.reason));
    show(recommendationBlock);
  }

  function renderTopics(topics) {
    clearChildren(topicsBody);

    if (!topics || topics.length === 0) {
      show(emptyMessage);
      return;
    }
    hide(emptyMessage);

    for (const topic of topics) {
      const row = document.createElement('tr');

      row.appendChild(createElement('td', { text: topic.topic }));
      row.appendChild(createElement('td', { text: `${Math.round(topic.accuracy * 100)}%` }));
      row.appendChild(createElement('td', { text: `${topic.correctCount} / ${topic.totalCount}` }));

      const statusCell = document.createElement('td');
      statusCell.appendChild(createElement('span', {
        className: `classification-badge classification-${topic.classification.toLowerCase()}`,
        text: topic.classification.replace(/_/g, ' '),
      }));
      row.appendChild(statusCell);

      topicsBody.appendChild(row);
    }
  }

  async function load() {
    hide(errorRegion);
    hide(contentRegion);
    show(loadingRegion);
    refreshButton.disabled = true;

    try {
      const [topics, recommendation] = await Promise.all([apiGetTopicProgress(), apiGetRecommendation()]);
      renderTopics(topics);
      renderRecommendation(recommendation);
      show(contentRegion);
    } catch (error) {
      errorRegion.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while loading progress. Please try again.';
      show(errorRegion);
    } finally {
      hide(loadingRegion);
      refreshButton.disabled = false;
    }
  }

  refreshButton.addEventListener('click', load);
  load();

  return { refresh: load };
}

/* ---------------------------------------------------------------------
 * UI: 3b. Quiz
 * ------------------------------------------------------------------- */

/**
 * `onSubmitted`, if provided, is called after a successful submission — used
 * to refresh the "Your progress" section immediately, since submitting a
 * quiz updates the weak-topic tracker server-side.
 */
function initQuizSection(onSubmitted) {
  const form = document.getElementById('quiz-form');
  const topicInput = document.getElementById('quiz-topic');
  const countInput = document.getElementById('quiz-count');
  const difficultySelect = document.getElementById('quiz-difficulty');
  const generateButton = document.getElementById('quiz-generate-button');
  const fieldError = document.getElementById('quiz-field-error');
  const loadingRegion = document.getElementById('quiz-loading');
  const errorRegion = document.getElementById('quiz-error');
  const questionsRegion = document.getElementById('quiz-questions-region');
  const questionsList = document.getElementById('quiz-questions-list');
  const submitError = document.getElementById('quiz-submit-error');
  const submitButton = document.getElementById('quiz-submit-button');
  const submittingRegion = document.getElementById('quiz-submitting');
  const resultsRegion = document.getElementById('quiz-results-region');
  const scoreSummary = document.getElementById('quiz-score-summary');
  const resultsList = document.getElementById('quiz-results-list');
  const retryButton = document.getElementById('quiz-retry-button');

  const state = { quizId: null, questions: [] };

  function validate() {
    if (!topicInput.value.trim()) {
      return 'Please enter a topic.';
    }
    const count = Number(countInput.value);
    if (!Number.isInteger(count) || count < 1 || count > 20) {
      return 'Number of questions must be a whole number between 1 and 20.';
    }
    return null;
  }

  function renderQuestions(questions) {
    clearChildren(questionsList);

    for (const question of questions) {
      const item = createElement('li', { className: 'quiz-question' });
      item.appendChild(createElement('p', { className: 'quiz-question-text', text: question.questionText }));

      const optionsGroup = createElement('div', {
        className: 'quiz-options',
        attrs: { role: 'radiogroup', 'aria-label': question.questionText },
      });

      question.options.forEach((optionText, index) => {
        const label = createElement('label', { className: 'quiz-option' });
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = `quiz-question-${question.questionId}`;
        radio.value = String(index);
        label.appendChild(radio);
        label.appendChild(document.createTextNode(` ${optionText}`));
        optionsGroup.appendChild(label);
      });

      item.appendChild(optionsGroup);
      questionsList.appendChild(item);
    }
  }

  /** Returns null if any question is left unanswered. */
  function collectAnswers() {
    const answers = [];
    for (const question of state.questions) {
      const selected = questionsList.querySelector(
        `input[name="quiz-question-${question.questionId}"]:checked`
      );
      if (!selected) {
        return null;
      }
      answers.push({ questionId: question.questionId, selectedOptionIndex: Number(selected.value) });
    }
    return answers;
  }

  function renderResults(response) {
    scoreSummary.textContent =
      `Score: ${response.correctCount} / ${response.totalCount} (${Math.round(response.accuracy * 100)}%)`;

    clearChildren(resultsList);
    const resultsByQuestionId = new Map(response.results.map((result) => [result.questionId, result]));

    for (const question of state.questions) {
      const result = resultsByQuestionId.get(question.questionId);
      const item = createElement('li', {
        className: `quiz-question quiz-result-${result.correct ? 'correct' : 'incorrect'}`,
      });
      item.appendChild(createElement('p', { className: 'quiz-question-text', text: question.questionText }));

      const optionsList = document.createElement('ul');
      optionsList.className = 'quiz-options-readonly';
      question.options.forEach((optionText, index) => {
        const optionItem = document.createElement('li');
        let label = optionText;
        if (index === result.correctOptionIndex) {
          label += ' (correct answer)';
          optionItem.className = 'quiz-option-correct';
        } else if (index === result.selectedOptionIndex) {
          label += ' (your answer)';
          optionItem.className = 'quiz-option-selected-wrong';
        }
        optionItem.textContent = label;
        optionsList.appendChild(optionItem);
      });
      item.appendChild(optionsList);

      item.appendChild(createElement('p', {
        className: 'quiz-result-verdict',
        text: result.correct ? 'Correct' : 'Incorrect',
      }));

      resultsList.appendChild(item);
    }
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    fieldError.textContent = '';
    hide(errorRegion);
    hide(questionsRegion);
    hide(resultsRegion);

    const validationError = validate();
    if (validationError) {
      fieldError.textContent = validationError;
      return;
    }

    const topic = topicInput.value.trim();
    const count = Number(countInput.value);
    const difficulty = difficultySelect.value;

    generateButton.disabled = true;
    show(loadingRegion);

    try {
      const result = await apiGenerateQuiz(topic, count, difficulty);
      if (!result.questions || result.questions.length === 0) {
        errorRegion.textContent = 'No quiz questions could be generated for this topic.';
        show(errorRegion);
        return;
      }
      state.quizId = result.quizId;
      state.questions = result.questions;
      submitError.textContent = '';
      renderQuestions(result.questions);
      show(questionsRegion);
    } catch (error) {
      errorRegion.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while generating the quiz. Please try again.';
      show(errorRegion);
    } finally {
      hide(loadingRegion);
      generateButton.disabled = false;
    }
  });

  submitButton.addEventListener('click', async () => {
    submitError.textContent = '';
    const answers = collectAnswers();
    if (!answers) {
      submitError.textContent = 'Please answer every question before submitting.';
      return;
    }

    submitButton.disabled = true;
    hide(questionsRegion);
    show(submittingRegion);

    try {
      const response = await apiSubmitQuiz(state.quizId, answers);
      renderResults(response);
      show(resultsRegion);
      if (typeof onSubmitted === 'function') {
        onSubmitted();
      }
    } catch (error) {
      submitError.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while submitting your answers. Please try again.';
      show(questionsRegion);
    } finally {
      hide(submittingRegion);
      submitButton.disabled = false;
    }
  });

  retryButton.addEventListener('click', () => {
    hide(resultsRegion);
    state.quizId = null;
    state.questions = [];
    form.reset();
  });
}

/* ---------------------------------------------------------------------
 * Theme toggle
 * ------------------------------------------------------------------- */

function initThemeToggle() {
  const button = document.getElementById('theme-toggle-button');

  function applyIcon(theme) {
    button.textContent = theme === 'dark' ? '☀️' : '🌙';
  }

  applyIcon(document.documentElement.getAttribute('data-theme') || 'light');

  button.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('studybuddy-theme', next);
    applyIcon(next);
  });
}

/* ---------------------------------------------------------------------
 * Tab navigation
 * ------------------------------------------------------------------- */

function initTabNav() {
  const buttons = Array.from(document.querySelectorAll('.tab-button'));

  function activate(tabName) {
    for (const button of buttons) {
      const isActive = button.dataset.tab === tabName;
      button.setAttribute('aria-selected', String(isActive));
    }
    for (const panel of document.querySelectorAll('[id^="tab-panel-"]')) {
      panel.hidden = panel.id !== `tab-panel-${tabName}`;
    }
  }

  for (const button of buttons) {
    button.addEventListener('click', () => activate(button.dataset.tab));
  }

  for (const gotoButton of document.querySelectorAll('[data-goto-tab]')) {
    gotoButton.addEventListener('click', () => activate(gotoButton.dataset.gotoTab));
  }

  activate('settings');
}

/* ---------------------------------------------------------------------
 * UI: 0. Settings (API keys) + configured-state gating
 * ------------------------------------------------------------------- */

function describeStatus(status) {
  return status.source === 'mock' ? 'Demo mode (mock)' : 'Saved (custom)';
}

function initKeyPanel(provider) {
  const badge = document.getElementById(`settings-${provider}-badge`);
  const maskedLabel = document.getElementById(`settings-${provider}-masked`);
  const input = document.getElementById(`settings-${provider}-input`);
  const showToggle = document.getElementById(`settings-${provider}-show`);
  const saveButton = document.getElementById(`settings-${provider}-save`);
  const clearButton = document.getElementById(`settings-${provider}-clear`);
  const message = document.getElementById(`settings-${provider}-message`);

  function render(status) {
    badge.textContent = describeStatus(status);
    badge.className = `classification-badge classification-${status.configured ? 'not_weak' : 'weak'}`;
    maskedLabel.textContent = status.maskedKey || '';
    clearButton.hidden = status.source !== 'saved';
  }

  showToggle.addEventListener('click', () => {
    input.type = input.type === 'password' ? 'text' : 'password';
  });

  saveButton.addEventListener('click', async () => {
    message.textContent = '';
    const apiKey = input.value.trim();
    if (!apiKey) {
      message.textContent = 'Please paste a key first.';
      return;
    }

    saveButton.disabled = true;
    try {
      const status = await apiSaveKey(provider, apiKey);
      render(status);
      input.value = '';
      message.textContent = '';
      await refreshGating();
    } catch (error) {
      message.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while saving this key. Please try again.';
    } finally {
      saveButton.disabled = false;
    }
  });

  clearButton.addEventListener('click', async () => {
    message.textContent = '';
    clearButton.disabled = true;
    try {
      const status = await apiClearKey(provider);
      render(status);
      await refreshGating();
    } catch (error) {
      message.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while clearing this key. Please try again.';
    } finally {
      clearButton.disabled = false;
    }
  });

  return { render };
}

function applyGating(status) {
  // Every feature stays usable even with no key: Mock Mode serves canned
  // responses instead of a 503. These banners are purely informational —
  // they tell the visitor *why* the output looks canned, they don't block
  // anything.
  const chatIsMock = status[status.chatProvider].source === 'mock';
  // Voice always uses the OpenAI key specifically, independent of which
  // provider is selected for embeddings.
  const voiceIsMock = status.openai.source === 'mock';

  document.getElementById('tutor-unconfigured-banner').hidden = !chatIsMock;
  document.getElementById('flashcard-unconfigured-banner').hidden = !chatIsMock;
  document.getElementById('quiz-unconfigured-banner').hidden = !chatIsMock;
  document.getElementById('voice-unconfigured-banner').hidden = !voiceIsMock;

  const voiceRecordButton = document.getElementById('voice-record-button');
  voiceRecordButton.disabled = !(typeof window.MediaRecorder !== 'undefined' && navigator.mediaDevices?.getUserMedia);
}

function initProviderSelect(kind, selectId) {
  const select = document.getElementById(selectId);

  select.addEventListener('change', async () => {
    const previous = select.dataset.current;
    try {
      const status = await apiSelectProvider(kind, select.value);
      select.dataset.current = select.value;
      renderAllPanels(status);
      applyGating(status);
    } catch (error) {
      select.value = previous;
    }
  });

  return {
    render(providerValue) {
      select.value = providerValue;
      select.dataset.current = providerValue;
    },
  };
}

let refreshGating = async () => {};
let renderAllPanels = () => {};

function initSettingsSection() {
  const panels = {
    claude: initKeyPanel('claude'),
    groq: initKeyPanel('groq'),
    openrouter: initKeyPanel('openrouter'),
    gemini: initKeyPanel('gemini'),
    openai: initKeyPanel('openai'),
  };
  const chatProviderSelect = initProviderSelect('chat-provider', 'settings-chat-provider-select');
  const embeddingProviderSelect = initProviderSelect('embedding-provider', 'settings-embedding-provider-select');

  function render(status) {
    panels.claude.render(status.claude);
    panels.groq.render(status.groq);
    panels.openrouter.render(status.openrouter);
    panels.gemini.render(status.gemini);
    panels.openai.render(status.openai);
    chatProviderSelect.render(status.chatProvider);
    embeddingProviderSelect.render(status.embeddingProvider);
  }
  renderAllPanels = render;

  async function loadAndApply() {
    const status = await apiGetSettingsStatus();
    render(status);
    applyGating(status);
  }

  refreshGating = loadAndApply;
  loadAndApply();
}

/* ---------------------------------------------------------------------
 * Bootstrap
 * ------------------------------------------------------------------- */

document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initTabNav();
  initSettingsSection();
  initUploadSection();
  initTutorSection();
  initVoiceInputSection(document.getElementById('tutor-question'));
  initFlashcardSection();
  const progress = initProgressSection();
  initQuizSection(progress.refresh);
});
