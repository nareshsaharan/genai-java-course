package com.studybuddy.flashcard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Configuration
public class FlashcardGeneratorConfig {

    @Bean
    public FlashcardGenerator flashcardGenerator(ChatModel chatModel) {
        return AiServices.builder(FlashcardGenerator.class)
                .chatModel(chatModel)
                .build();
    }
}
