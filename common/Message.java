package common;

import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        TEXT, FILE, PRIVATE, GROUP, CONFIRM_READ, USER_LIST, NOTIFICATION
    }

    private String sender;
    private String receiver;
    private String content;
    private byte[] fileData;
    private String fileName;
    private MessageType type;
    private Date timestamp;

    public Message(String sender, String receiver, String content, MessageType type) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.type = type;
        this.timestamp = new Date();
    }

    // Getters e Setters
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public byte[] getFileData() { return fileData; }
    public String getFileName() { return fileName; }
    public MessageType getType() { return type; }
    public Date getTimestamp() { return timestamp; }

    public void setFileData(byte[] data) { this.fileData = data; }
    public void setFileName(String name) { this.fileName = name; }
}
