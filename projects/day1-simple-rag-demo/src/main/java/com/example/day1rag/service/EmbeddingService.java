package com.example.day1rag.service;

/**
 * Converts text into an "embedding" — a fixed-size list of numbers
 * (a vector) that is meant to represent the meaning of the text.
 *
 * Why do we need this? Computers can't compare the "meaning" of two
 * pieces of text directly, but they CAN compare two lists of numbers
 * (e.g. how close they are to each other). So we turn text into numbers
 * first, then use math (like cosine similarity, in a later lesson) to
 * find chunks that are "similar" to a user's question.
 *
 * In production, embeddings come from a real, trained AI model (for
 * example OpenAI's or Anthropic's embedding models). Those models learn
 * from huge amounts of text so that semantically similar sentences end
 * up with similar vectors. This demo uses a FAKE embedding
 * (see FakeEmbeddingService) so students can see the shape of a RAG
 * pipeline without needing an API key or a real model.
 */
public interface EmbeddingService {

    double[] embed(String text);
}
