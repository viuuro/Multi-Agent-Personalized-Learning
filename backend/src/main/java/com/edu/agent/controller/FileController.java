package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.UploadedFileRecord;
import com.edu.agent.repository.UploadedFileRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器 —— 接收前端文件并转发给 Python AI 服务解析
 */
@RestController
@RequestMapping("/api")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;

    private final UploadedFileRecordRepository uploadedFileRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FileController(UploadedFileRecordRepository uploadedFileRepository) {
        this.uploadedFileRepository = uploadedFileRepository;
    }

    /**
     * POST /api/parse-file —— 上传文件并提取文本内容
     *
     * 接收前端上传的文件，转发给 Python AI 服务的 /parse-file 端点解析。
     * 支持格式：PDF (.pdf)、Word (.docx)、纯文本 (.txt)
     */
    @PostMapping("/parse-file")
    public ApiResponse<Map<String, String>> parseFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "CHAT") String purpose) {
        log.info(">>> POST /api/parse-file —— filename: {}, size: {}", file.getOriginalFilename(), file.getSize());

        try {
            // 构造 multipart 请求转发给 Python AI
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename() == null
                    ? "未命名文件" : file.getOriginalFilename();
            String filename = originalFilename.replaceAll("[\\r\\n\\\"]", "_");

            // 构造 multipart body
            String header = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            String footer = "\r\n--" + boundary + "--\r\n";

            byte[] body = concat(header.getBytes(), fileBytes, footer.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/parse-file"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, String> result = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response.body(), Map.class);
                if (userId != null) {
                    UploadedFileRecord record = new UploadedFileRecord();
                    record.setUserId(userId);
                    record.setConversationId(conversationId == null || conversationId.isBlank()
                            ? null : conversationId.trim());
                    record.setFileName(originalFilename);
                    record.setSizeBytes(file.getSize());
                    record.setPurpose("SUBMISSION".equalsIgnoreCase(purpose)
                            ? "SUBMISSION" : "CHAT");
                    uploadedFileRepository.save(record);
                }
                return ApiResponse.success(result);
            } else {
                log.error("Python AI 返回错误: {} {}", response.statusCode(), response.body());
                return ApiResponse.error(500, "文件解析服务异常");
            }
        } catch (Exception e) {
            log.error("文件解析失败: {}", e.getMessage());
            return ApiResponse.error(500, "文件解析失败: " + e.getMessage());
        }
    }

    /** GET /api/files —— 恢复用户的历史文件元数据。 */
    @GetMapping("/files")
    public ApiResponse<List<UploadedFileRecord>> getFileHistory(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return ApiResponse.success("ok", uploadedFileRepository
                .findByUserIdOrderByUploadedAtDesc(userId, PageRequest.of(0, safeLimit)));
    }

    private byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] result = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length + b.length, c.length);
        return result;
    }
}
