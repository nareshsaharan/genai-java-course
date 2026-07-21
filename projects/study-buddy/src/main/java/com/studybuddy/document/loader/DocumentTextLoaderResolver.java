package com.studybuddy.document.loader;

import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.UnsupportedFileTypeException;

@Component
public class DocumentTextLoaderResolver {

    private final List<DocumentTextLoader> loaders;

    public DocumentTextLoaderResolver(List<DocumentTextLoader> loaders) {
        this.loaders = loaders;
    }

    public DocumentTextLoader resolve(String filename) {
        return loaders.stream()
                .filter(loader -> loader.supports(filename))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "Unsupported file type for '" + filename + "'. Supported types: PDF, TXT, Markdown."));
    }
}
