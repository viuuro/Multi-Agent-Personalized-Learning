package com.edu.agent;

import com.edu.agent.model.Conversation;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.service.ArtifactPersistenceService;
import com.edu.agent.service.ConversationSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArtifactPersistenceServiceTest {

    @Test
    void persistsPromptAndGeneratedImageInTheSameConversation() {
        ConversationRepository repository = mock(ConversationRepository.class);
        ConversationSessionService sessions = mock(ConversationSessionService.class);
        AtomicLong ids = new AtomicLong(10);
        when(repository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation message = invocation.getArgument(0);
            message.setId(ids.incrementAndGet());
            return message;
        });
        ArtifactPersistenceService service = new ArtifactPersistenceService(repository, sessions);
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
                "dataUrl", "data:image/png;base64,aW1hZ2U="));

        String conversationId = service.persistGeneratedImage(
                7L, "conversation-7", "绘制进程状态图", result);

        assertThat(conversationId).isEqualTo("conversation-7");
        assertThat(result).containsEntry("conversationId", "conversation-7")
                .containsEntry("messageId", 12L);
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(repository, times(2)).save(captor.capture());
        List<Conversation> messages = captor.getAllValues();
        assertThat(messages).extracting(Conversation::getRole)
                .containsExactly("user", "assistant");
        assertThat(messages.get(1).getAttachmentData()).isEqualTo(result.get("dataUrl"));
        assertThat(messages.get(1).getAttachmentType()).isEqualTo("image");
        verify(sessions).ensureSession(7L, "conversation-7");
    }

    @Test
    void rejectsTemporaryRemoteUrlInsteadOfPersistingIt() {
        ConversationRepository repository = mock(ConversationRepository.class);
        ConversationSessionService sessions = mock(ConversationSessionService.class);
        ArtifactPersistenceService service = new ArtifactPersistenceService(repository, sessions);

        assertThatThrownBy(() -> service.persistGeneratedImage(
                7L, "conversation-7", "知识图", new LinkedHashMap<>(Map.of(
                        "dataUrl", "https://temporary.example/image.png"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("可持久化");
        verifyNoInteractions(repository, sessions);
    }
}
