package com.edu.agent.service;

import com.edu.agent.model.LearningResourceFeedback;
import com.edu.agent.repository.LearningResourceFeedbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LearningResourceFeedbackServiceTest {

    @Test
    void recordsExplicitPreferenceAndProducesRankingScore() {
        LearningResourceFeedbackRepository repository = mock(LearningResourceFeedbackRepository.class);
        when(repository.findByUserIdAndResourceKey(eq(7L), anyString())).thenReturn(Optional.empty());
        LearningResourceFeedbackService service = new LearningResourceFeedbackService(repository, new ObjectMapper());

        service.record(7L, "conversation-7", "https://dev.java/learn/", "Dev.java", "HELPFUL");

        ArgumentCaptor<LearningResourceFeedback> captor = ArgumentCaptor.forClass(LearningResourceFeedback.class);
        verify(repository).save(captor.capture());
        LearningResourceFeedback saved = captor.getValue();
        assertThat(saved.getHelpfulScore()).isEqualTo(1);
        when(repository.findTop100ByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(saved));
        assertThat(service.rankingJson(7L)).contains("https://dev.java/learn/", "45.0");
    }
}
