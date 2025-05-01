package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import common.Message;
import common.MessageType;

public class PrivateChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private String sender;
    private String receiver;
    private Client client;

    // Cores
    private final Color darkBg = new Color(30, 30, 30);
    private final Color lightText = new Color(220, 220, 220);

    public PrivateChatWindow(String sender, String receiver, Client client) {
        this.sender = sender;
        this.receiver = receiver;
        this.client = client;

        configureWindow();
        initComponents();
        setVisible(true);
    }

    private void configureWindow() {
        setTitle("Chat: " + receiver);
        setSize(300, 500);
        setMinimumSize(new Dimension(280, 400));
        setLocationRelativeTo(null);
        setResizable(true);
        getContentPane().setBackground(darkBg);
        setLayout(new BorderLayout(5, 5));
    }

    private void initComponents() {
        // Área de Chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(darkBg);
        chatArea.setForeground(lightText);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Painel Inferior
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        bottomPanel.setBackground(darkBg);

        inputField = new JTextField();
        inputField.setBackground(new Color(50, 50, 50));
        inputField.setForeground(lightText);
        inputField.addActionListener(e -> sendMessage());
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JButton fileBtn = new JButton("📎");
        fileBtn.setPreferredSize(new Dimension(45, 30));
        fileBtn.setBackground(new Color(0, 150, 70));
        fileBtn.setForeground(Color.WHITE);
        fileBtn.addActionListener(e -> sendFile());
        bottomPanel.add(fileBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            Message msg = new Message(sender, receiver, text, MessageType.PRIVATE);
            client.sendMessage(msg);
            chatArea.append("[Você]: " + text + "\n");
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
                chatArea.append("[Você] (Arquivo): " + file.getName() + "\n");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Erro ao enviar arquivo.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void appendMessage(Message msg) {
        chatArea.append("[" + msg.getSender() + "]: " + msg.getContent() + "\n");
    }
}