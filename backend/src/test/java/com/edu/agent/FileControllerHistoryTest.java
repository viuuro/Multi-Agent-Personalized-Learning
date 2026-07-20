package com.edu.agent;

import com.edu.agent.controller.FileController;
import com.edu.agent.model.Conversation;
import com.edu.agent.model.UploadedFileRecord;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.UploadedFileRecordRepository;
import com.edu.agent.service.KnowledgeBaseService;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileControllerHistoryTest {

    @Test
    void historyMergesUploadedDocumentsAndUserImages() {
        UploadedFileRecordRepository files = mock(UploadedFileRecordRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        LocalDateTime now = LocalDateTime.now();
        UploadedFileRecord document = new UploadedFileRecord();
        document.setId(3L);
        document.setUserId(7L);
        document.setFileName("notes.pdf");
        document.setSizeBytes(1200L);
        document.setPurpose("CHAT");
        document.setUploadedAt(now.minusMinutes(1));
        Conversation image = new Conversation();
        image.setId(9L);
        image.setUserId(7L);
        image.setConversationId("conversation-7");
        image.setRole("user");
        image.setAttachmentType("image");
        image.setAttachmentName("diagram.png");
        image.setAttachmentData("data:image/png;base64,aW1hZ2U=");
        image.setTimestamp(now);
        when(files.findByUserIdOrderByUploadedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(document));
        when(conversations.findByUserIdAndRoleAndAttachmentTypeOrderByTimestampDesc(
                eq(7L), eq("user"), eq("image"), any(Pageable.class)))
                .thenReturn(List.of(image));
        FileController controller = new FileController(
                files, conversations, mock(RequestRateLimiter.class),
                new ObjectMapper(), mock(KnowledgeBaseService.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("7");

        List<UploadedFileRecord> history = controller
                .getFileHistory(null, authentication, 50).getData();

        assertThat(history).extracting(UploadedFileRecord::getFileName)
                .containsExactly("diagram.png", "notes.pdf");
        assertThat(history).extracting(UploadedFileRecord::getFileKind)
                .containsExactly("IMAGE", "DOCUMENT");
        assertThat(history.get(0).getId()).isEqualTo(-9L);
    }
}
