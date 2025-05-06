package common;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    private String messageId;     // ID único da mensagem para rastreamento
    private String sender;
    private String receiver;
    private String content;
    private Date timestamp;
    private MessageType type;
    private byte[] fileData;
    private String fileName;
    private MessageStatus status;  // Status atual da mensagem
    private Date deliveredTime;    // Quando a mensagem foi entregue
    private Date readTime;         // Quando a mensagem foi lida

    public Message(String sender, String receiver, String content, MessageType type) {
        this.messageId = UUID.randomUUID().toString();
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.type = type;
        this.timestamp = new Date();
        this.status = MessageStatus.SENDING;
    }

    // Getters e Setters básicos
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
    
    public void setFileData(byte[] fileData) { this.fileData = fileData; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
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
        return String.format("Message[id=%s, from=%s, to=%s, type=%s, status=%s]", 
                messageId, sender, receiver, type, status);
    }
}