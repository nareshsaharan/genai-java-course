package com.studybuddy.quiz;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Configuration
public class QuizGeneratorConfig {

    @Bean
    public QuizGenerator quizGenerator(ChatModel chatModel) {
        return AiServices.builder(QuizGenerator.class)
                .chatModel(chatModel)
                .build();
    }
}
