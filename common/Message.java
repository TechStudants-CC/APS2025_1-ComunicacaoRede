package common;

import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sender;
    private String receiver;
    private String content;
    private Date timestamp;
    private MessageType type;
    private byte[] fileData;
    private String fileName;

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
    public Date getTimestamp() { return timestamp; }
    public MessageType getType() { return type; }
    public byte[] getFileData() { return fileData; }
    public String getFileName() { return fileName; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}