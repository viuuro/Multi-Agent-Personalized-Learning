package com.edu.agent.model;

/**
 * 聊天消息 DTO —— 前后端通信的数据传输对象
 *
 * 用于 POST /api/chat 接口接收前端请求，不持久化到数据库。
 * conversationId 可选：首次对话为空时，后端会自动生成并返回。
 */
public class ChatMessage {

    /** 用户输入的消息内容 */
    private String message;

    /** 会话 ID，用于关联同一轮对话的多条消息。首次对话可为空 */
    private String conversationId;

    /** 图片 Base64 数据（可选，用于多模态图片识别） */
    private String imageData;

    /** 用户 ID，用于画像按用户隔离 */
    private Long userId;

    /** 前端展示用文本；AI message 可包含文件解析全文。 */
    private String displayMessage;

    /** 附件名称（图片或文件）。 */
    private String attachmentName;

    /** 附件类型：image/file。 */
    private String attachmentType;

    public ChatMessage() {}

    public ChatMessage(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getImageData() { return imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDisplayMessage() { return displayMessage; }
    public void setDisplayMessage(String displayMessage) { this.displayMessage = displayMessage; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
}
