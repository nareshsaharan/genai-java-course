package com.studybuddy.tutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Configuration
public class TutorAssistantConfig {

    @Bean
    public TutorAssistant tutorAssistant(ChatModel chatModel) {
        return AiServices.builder(TutorAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
