package client;

import javax.swing.*;
import java.awt.*;
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

        configureWindow();
        initComponents();
        setVisible(true);
    }

    private void configureWindow() {
        setTitle("Grupo: " + String.join(", ", members));
        setSize(300, 500);
        setMinimumSize(new Dimension(280, 400));
        setLocationRelativeTo(null);
        setResizable(true);
        getContentPane().setBackground(darkBg);
        setLayout(new BorderLayout(5, 5));
    }

    private void initComponents() {
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(darkBg);
        chatArea.setForeground(lightText);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setBackground(new Color(50, 50, 50));
        inputField.setForeground(lightText);
        inputField.addActionListener(e -> sendMessage());
        add(inputField, BorderLayout.SOUTH);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            Message msg = new Message(sender, String.join(",", members), text, MessageType.GROUP);
            client.sendMessage(msg);
            chatArea.append("[Você]: " + text + "\n");
            inputField.setText("");
        }
    }
}