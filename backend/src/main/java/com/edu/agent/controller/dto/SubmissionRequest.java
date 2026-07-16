package com.edu.agent.controller.dto;

/**
 * 提交任务成果的请求 DTO
 *
 * 前端在调用 POST /api/submissions 时，需要提供此 JSON 格式的请求体。
 *
 * ========== 请求体示例 ==========
 *   {
 *     "taskId": 1,
 *     "content": "这是我在本次任务中完成的学习成果报告...包含了代码示例和总结"
 *   }
 *
 * ========== 字段说明 ==========
 *   taskId  — 任务 ID，标识要提交的是哪个任务的成果
 *   content — 提交的成果内容（文本），可以是文字描述、代码片段、链接等
 */
public class SubmissionRequest {

    /** 任务 ID，对应 learning_task 表的 id 字段 */
    private Long taskId;

    /** 提交的成果内容（文本），AI 将据此进行评分和分析 */
    private String content;

    /** 当前对话 ID；新前端以此解析计划和任务。 */
    private String conversationId;

    private String fileName;

    private Long fileSize;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
