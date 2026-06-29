package com.example.aichatbot.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ImageStore – holds generated images in memory, keyed by a unique ID.
 *
 * Why store images?
 * The OpenAI API returns the image as raw bytes (base64).  We can't give
 * the caller those bytes as a "clickable URL".  Instead we:
 *   1. Save the bytes here under a UUID.
 *   2. Return a URL like /api/multimodal/image/{id}.
 *   3. When the browser hits that URL, we read the bytes back and serve them.
 *
 * Limitation: images are lost on server restart (same as ConversationStore).
 */
@Component
public class ImageStore {

    // key = imageId (UUID string), value = raw PNG bytes
    private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

    public void save(String imageId, byte[] imageBytes) {
        store.put(imageId, imageBytes);
    }

    public byte[] get(String imageId) {
        return store.get(imageId);
    }
}
