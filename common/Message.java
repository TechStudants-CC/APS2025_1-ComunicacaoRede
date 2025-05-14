// common/Message.java
package common;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    private String messageId;
    private String sender;
    private String receiver;
    private String content;
    private Date timestamp;
    private MessageType type;
    private byte[] fileData;
    private String fileName;
    private MessageStatus status;
    private Date deliveredTime;
    private Date readTime;

    public Message(String messageId, String sender, String receiver, String content, MessageType type) {
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.type = type;
        this.timestamp = new Date();
        if (type != MessageType.STATUS_UPDATE && type != MessageType.MESSAGE_READ && type != MessageType.USER_LIST) {
            this.status = MessageStatus.SENDING;
        }
    }
    
    public Message(String sender, String receiver, String content, MessageType type) {
        this(UUID.randomUUID().toString(), sender, receiver, content, type);
    }

    // Getters
    public String getMessageId() { return messageId; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public Date getTimestamp() { return timestamp; }
    public MessageType getType() { return type; }
    public byte[] getFileData() { return fileData; }
    public String getFileName() { return fileName; }
    public MessageStatus getStatus() { return status; }
    public Date getDeliveredTime() { return deliveredTime; }
    public Date getReadTime() { return readTime; }

    // Setters
    public void setFileData(byte[] fileData) { this.fileData = fileData; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setDeliveredTime(Date deliveredTime) { this.deliveredTime = deliveredTime; }
    public void setReadTime(Date readTime) { this.readTime = readTime; }
    public void setContent(String content) { this.content = content; }
    public void setReceiver(String receiver) { this.receiver = receiver; } // Setter adicionado

    // Métodos para atualizar status
    public void markAsSent() {
        this.status = MessageStatus.SENT;
    }

    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.deliveredTime = new Date();
    }

    public void markAsRead() {
        this.status = MessageStatus.READ;
        this.readTime = new Date();
    }

    public void markAsFailed() {
        this.status = MessageStatus.FAILED;
    }

    @Override
    public String toString() {
        return String.format("Message[id=%s, from=%s, to=%s, type=%s, status=%s, content=%s]",
                messageId, sender, receiver, type, status, (content != null && content.length() > 20 ? content.substring(0,20)+"..." : content) );
    }
}