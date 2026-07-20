package com.edu.agent.service;

import com.edu.agent.model.Conversation;
import com.edu.agent.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将生成式资源转换为普通对话消息，保证刷新和切换会话后仍可恢复。 */
@Service
public class ArtifactPersistenceService {

    private static final int MAX_PERSISTED_IMAGE_DATA_URL_LENGTH = 15_000_000;
    private static final Pattern IMAGE_DATA_URL = Pattern.compile(
            "^data:image/(png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$",
            Pattern.CASE_INSENSITIVE);

    private final ConversationRepository conversationRepository;
    private final ConversationSessionService conversationSessionService;

    public ArtifactPersistenceService(ConversationRepository conversationRepository,
                                      ConversationSessionService conversationSessionService) {
        this.conversationRepository = conversationRepository;
        this.conversationSessionService = conversationSessionService;
    }

    @Transactional
    public String persistGeneratedImage(Long userId,
                                        String requestedConversationId,
                                        String prompt,
                                        Map<String, Object> result) {
        String dataUrl = String.valueOf(result.getOrDefault("dataUrl", "")).trim();
        if (dataUrl.length() > MAX_PERSISTED_IMAGE_DATA_URL_LENGTH) {
            throw new IllegalArgumentException("生成图片过大，无法安全持久化");
        }
        Matcher matcher = IMAGE_DATA_URL.matcher(dataUrl);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("图片生成服务没有返回可持久化的图片数据");
        }

        String conversationId = normalizeConversationId(requestedConversationId);
        conversationSessionService.ensureSession(userId, conversationId);

        LocalDateTime now = LocalDateTime.now();
        Conversation request = message(userId, conversationId, "user",
                "【生成图片】" + prompt, now);
        conversationRepository.save(request);

        String extension = "jpeg".equalsIgnoreCase(matcher.group(1))
                || "jpg".equalsIgnoreCase(matcher.group(1)) ? "jpg" : matcher.group(1).toLowerCase();
        Conversation image = message(userId, conversationId, "assistant",
                "学习图片已生成。", now.plusNanos(1_000_000));
        image.setAttachmentName("学习图片-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "." + extension);
        image.setAttachmentType("image");
        image.setAttachmentData(dataUrl);
        conversationRepository.save(image);

        result.put("conversationId", conversationId);
        result.put("messageId", image.getId());
        return conversationId;
    }

    private Conversation message(Long userId, String conversationId, String role,
                                 String content, LocalDateTime timestamp) {
        Conversation message = new Conversation();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setAiContent(content);
        message.setTimestamp(timestamp);
        return message;
    }

    private String normalizeConversationId(String value) {
        String conversationId = value == null ? "" : value.trim();
        if (conversationId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return conversationId.substring(0, Math.min(conversationId.length(), 64));
    }
}
