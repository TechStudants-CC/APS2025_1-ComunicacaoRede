package client;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import common.Message;
import common.MessageType;

public class GroupChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private List<String> members;
    private String sender;
    private Client client;

    // Cores
    private final Color darkBg = new Color(30, 30, 30);
    private final Color lightText = new Color(220, 220, 220);

    public GroupChatWindow(String sender, List<String> members, Client client) {
        this.sender = sender;
        this.members = members;
        this.client = client;

        setTitle("Grupo: " + String.join(", ", members));
        setSize(600, 400);
        setLayout(new BorderLayout());
        getContentPane().setBackground(darkBg);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(darkBg);
        chatArea.setForeground(lightText);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(darkBg);

        inputField = new JTextField();
        inputField.setBackground(new Color(50, 50, 50));
        inputField.setForeground(lightText);
        inputField.addActionListener(e -> sendMessage());
        bottom.add(inputField, BorderLayout.CENTER);

        JButton fileBtn = new JButton("📎");
        fileBtn.setBackground(new Color(0, 150, 70));
        fileBtn.setForeground(Color.WHITE);
        fileBtn.addActionListener(e -> sendFile());
        bottom.add(fileBtn, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            Message msg = new Message(sender, String.join(",", members), text, MessageType.GROUP);
            client.sendMessage(msg);
            appendMessage(new Message(sender, String.join(",", members), text, MessageType.GROUP));
            inputField.setText("");
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                Message msg = new Message(sender, String.join(",", members), "Arquivo: " + file.getName(), MessageType.FILE);
                msg.setFileData(data);
                msg.setFileName(file.getName());
                client.sendMessage(msg);
                appendMessage(msg); // Adiciona a mensagem ao chat
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Erro ao enviar arquivo: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void appendMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            if (msg.getType() == MessageType.FILE) {
                chatArea.append(msg.getSender() + ": Arquivo: " + msg.getFileName() + "\n");
            } else {
                 chatArea.append(msg.getSender() + ": " + msg.getContent() + "\n");
            }
        });
    }
}