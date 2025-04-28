package client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import common.Message;
import common.Message.MessageType;

public class GroupChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private List<String> members;
    private String sender;
    private Client client;

    public GroupChatWindow(String sender, List<String> members, Client client) {
        this.sender = sender;
        this.members = members;
        this.client = client;

        setTitle("Grupo: " + String.join(", ", members));
        setSize(600, 400);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.addActionListener(e -> sendGroupMessage());
        add(inputField, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void sendGroupMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            Message msg = new Message(sender, String.join(",", members), text, MessageType.GROUP);
            client.sendMessage(msg);
            chatArea.append("[Você]: " + text + "\n");
            inputField.setText("");
        }
    }
}
