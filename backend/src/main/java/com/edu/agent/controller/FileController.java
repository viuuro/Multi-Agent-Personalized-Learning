package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.UploadedFileRecord;
import com.edu.agent.model.Conversation;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.UploadedFileRecordRepository;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.KnowledgeBaseService;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * 文件上传控制器 —— 接收前端文件并转发给 Python AI 服务解析
 */
@RestController
@RequestMapping("/api")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of("pdf", "docx", "txt", "md");

    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;

    private final UploadedFileRecordRepository uploadedFileRepository;
    private final ConversationRepository conversationRepository;
    private final RequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FileController(UploadedFileRecordRepository uploadedFileRepository,
                          ConversationRepository conversationRepository,
                          RequestRateLimiter rateLimiter,
                          ObjectMapper objectMapper,
                          KnowledgeBaseService knowledgeBaseService) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.conversationRepository = conversationRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * POST /api/parse-file —— 上传文件并提取文本内容
     *
     * 接收前端上传的文件，转发给 Python AI 服务的 /parse-file 端点解析。
     * 支持格式：PDF (.pdf)、Word (.docx)、纯文本 (.txt)
     */
    @PostMapping("/parse-file")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parseFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long ignoredUserId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "CHAT") String purpose,
            Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (!rateLimiter.tryAcquire("file:" + userId, 20, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(429, "文件解析过于频繁，请稍后再试"));
        }
        log.info(">>> POST /api/parse-file —— filename: {}, size: {}", file.getOriginalFilename(), file.getSize());

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT)
                : "";
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "文件不能为空"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(413, "文件不能超过 10MB"));
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "仅支持 PDF、DOCX、TXT 和 Markdown 文件"));
        }

        try {
            // 构造 multipart 请求转发给 Python AI
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] fileBytes = file.getBytes();
            String filename = originalFilename.replaceAll("[\\r\\n\\\"]", "_");

            // 构造 multipart body
            String header = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            String footer = "\r\n--" + boundary + "--\r\n";

            byte[] body = concat(
                    header.getBytes(StandardCharsets.UTF_8),
                    fileBytes,
                    footer.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/parse-file"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                UploadedFileRecord record = new UploadedFileRecord();
                record.setUserId(userId);
                record.setConversationId(conversationId == null || conversationId.isBlank()
                        ? null : conversationId.trim());
                record.setFileName(originalFilename);
                record.setSizeBytes(file.getSize());
                String normalizedPurpose = switch (purpose == null
                        ? "CHAT" : purpose.trim().toUpperCase(java.util.Locale.ROOT)) {
                    case "SUBMISSION" -> "SUBMISSION";
                    case "KNOWLEDGE" -> "KNOWLEDGE";
                    default -> "CHAT";
                };
                record.setPurpose(normalizedPurpose);
                uploadedFileRepository.save(record);

                // 前端只使用 text 预览；完整解析正文仅在后端入库，避免污染聊天消息。
                Object fullTextValue = result.remove("full_text");
                String fullText = fullTextValue == null
                        ? String.valueOf(result.getOrDefault("text", ""))
                        : String.valueOf(fullTextValue);
                try {
                    var knowledgeDocument = knowledgeBaseService.indexParsedFile(
                            userId, conversationId, originalFilename, normalizedPurpose, fullText);
                    result.put("knowledge_document_id", knowledgeDocument.getId());
                    result.put("knowledge_chunk_count", knowledgeDocument.getChunkCount());
                    result.put("knowledge_scope", knowledgeDocument.getScope());
                } catch (Exception indexException) {
                    log.warn("文件已解析但知识库索引失败: {}", indexException.getMessage());
                    result.put("knowledge_indexed", false);
                }
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                log.error("Python AI 返回错误: {} {}", response.statusCode(), response.body());
                int status = response.statusCode() == 413 ? 413 : 422;
                return ResponseEntity.status(status).body(ApiResponse.error(status, "文件无法解析"));
            }
        } catch (Exception e) {
            log.error("文件解析失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error(502, "文件解析服务暂时不可用"));
        }
    }

    /** GET /api/files —— 恢复用户的历史文件元数据。 */
    @GetMapping("/files")
    public ApiResponse<List<UploadedFileRecord>> getFileHistory(
            @RequestParam(required = false) Long userId,
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        userId = CurrentUser.id(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<UploadedFileRecord> history = new ArrayList<>(uploadedFileRepository
                .findByUserIdOrderByUploadedAtDesc(userId, PageRequest.of(0, safeLimit)));
        history.forEach(item -> item.setFileKind("DOCUMENT"));
        conversationRepository.findByUserIdAndRoleAndAttachmentTypeOrderByTimestampDesc(
                        userId, "user", "image", PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toImageHistoryItem)
                .forEach(history::add);
        history.sort(Comparator.comparing(UploadedFileRecord::getUploadedAt).reversed());
        return ApiResponse.success("ok", history.stream().limit(safeLimit).toList());
    }

    private UploadedFileRecord toImageHistoryItem(Conversation message) {
        UploadedFileRecord item = new UploadedFileRecord();
        // 文件记录主键均为正数；负值为图片消息在历史列表中的稳定虚拟 ID。
        item.setId(message.getId() == null ? Long.MIN_VALUE : -message.getId());
        item.setUserId(message.getUserId());
        item.setConversationId(message.getConversationId());
        item.setFileName(imageName(message));
        item.setSizeBytes(estimateDataUrlBytes(message.getAttachmentData()));
        item.setPurpose("CHAT");
        item.setFileKind("IMAGE");
        item.setUploadedAt(message.getTimestamp());
        return item;
    }

    private String imageName(Conversation message) {
        if (message.getAttachmentName() != null && !message.getAttachmentName().isBlank()) {
            return message.getAttachmentName();
        }
        String data = message.getAttachmentData() == null ? "" : message.getAttachmentData();
        String extension = data.startsWith("data:image/jpeg") ? "jpg"
                : data.startsWith("data:image/webp") ? "webp" : "png";
        return "上传图片." + extension;
    }

    private long estimateDataUrlBytes(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return 0L;
        int comma = dataUrl.indexOf(',');
        String encoded = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
        int padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
        return Math.max(0L, encoded.length() * 3L / 4L - padding);
    }

    private byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] result = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length + b.length, c.length);
        return result;
    }

    private static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null) return "";
        String normalized = rawFilename.replace('\\', '/');
        int lastSeparator = normalized.lastIndexOf('/');
        String basename = lastSeparator >= 0 ? normalized.substring(lastSeparator + 1) : normalized;
        basename = basename.replaceAll("[\\p{Cntrl}]", "_").trim();
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }
}
