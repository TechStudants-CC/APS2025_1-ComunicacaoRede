package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import common.Message;
import common.Message.MessageType;

public class PrivateChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private String sender;
    private String receiver;
    private Client client;

    public PrivateChatWindow(String sender, String receiver, Client client) {
        this.sender = sender;
        this.receiver = receiver;
        this.client = client;

        setTitle("Chat Privado com: " + receiver);
        setSize(500, 400);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendPrivateMessage());
        bottom.add(inputField, BorderLayout.CENTER);

        JButton sendFileBtn = new JButton("📎");
        sendFileBtn.setToolTipText("Enviar Arquivo");
        sendFileBtn.addActionListener(e -> sendFile());
        bottom.add(sendFileBtn, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void sendPrivateMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            Message msg = new Message(sender, receiver, text, MessageType.PRIVATE);
            client.sendMessage(msg);
            appendMessage(msg);
            inputField.setText("");
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                Message msg = new Message(sender, receiver, "Arquivo: " + file.getName(), MessageType.FILE);
                msg.setFileData(data);
                msg.setFileName(file.getName());
                client.sendMessage(msg);
                appendMessage(msg);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Erro ao enviar arquivo.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void appendMessage(Message msg) {
        String time = "[" + msg.getTimestamp().toString().substring(11, 16) + "]";
        chatArea.append(time + " " + msg.getSender() + ": " + msg.getContent() + "\n");
    }
}
