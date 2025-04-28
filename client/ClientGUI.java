package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import common.Message;

public class ClientGUI extends JFrame {
    private DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username;
    private Map<String, PrivateChatWindow> privateChats = new HashMap<>();

    public ClientGUI() {
        setTitle("Chat Ambiental - Cliente");
        setSize(800, 600);
        setLayout(new BorderLayout());

        // Área de usuários
        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(200, 0));
        add(userScroll, BorderLayout.WEST);

        // Área de botões
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        JButton startChatButton = new JButton("Iniciar Chat Direto");
        startChatButton.addActionListener(e -> startPrivateChat());
        JButton createGroupButton = new JButton("Criar Grupo");
        createGroupButton.addActionListener(e -> createGroup());
        buttonPanel.add(startChatButton);
        buttonPanel.add(createGroupButton);
        add(buttonPanel, BorderLayout.CENTER);

        connect();
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void connect() {
        username = JOptionPane.showInputDialog(this, "Digite seu nome de usuário:");
        if (username != null && !username.trim().isEmpty()) {
            try {
                client = new Client("127.0.0.1", 54321, username, this);
            } catch (IOException e) {
                showError("Não foi possível conectar ao servidor.");
            }
        } else {
            showError("Nome de usuário inválido. Encerrando.");
            System.exit(0);
        }
    }

    private void startPrivateChat() {
        String receiver = userList.getSelectedValue();
        if (receiver != null && !receiver.equals(username)) {
            PrivateChatWindow chat = privateChats.get(receiver);
            if (chat == null) {
                chat = new PrivateChatWindow(username, receiver, client);
                privateChats.put(receiver, chat);
            }
            chat.setVisible(true);
            chat.toFront();
        }
    }

    private void createGroup() {
        DefaultListModel<String> groupModel = new DefaultListModel<>();
        for (int i = 0; i < userModel.size(); i++) {
            String user = userModel.getElementAt(i);
            if (!user.equals(username)) {
                groupModel.addElement(user);
            }
        }
        JList<String> groupList = new JList<>(groupModel);
        groupList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scroll = new JScrollPane(groupList);
        scroll.setPreferredSize(new Dimension(200, 250));

        int result = JOptionPane.showConfirmDialog(this, scroll, "Selecione usuários para o grupo", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            List<String> selectedUsers = groupList.getSelectedValuesList();
            if (selectedUsers.size() >= 2) {
                new GroupChatWindow(username, selectedUsers, client);
            } else {
                showError("Selecione pelo menos 2 usuários.");
            }
        }
    }

    public void handleMessage(Message msg) {
        switch (msg.getType()) {
            case USER_LIST:
                updateUserList(msg.getContent());
                break;
            case PRIVATE:
                showPrivateMessage(msg);
                break;
            case GROUP:
                showGroupMessage(msg);
                break;
            case FILE:
                showFileMessage(msg);
                break;
            default:
                break;
        }
    }

    private void updateUserList(String list) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : list.split(",")) {
                if (!user.trim().isEmpty() && !user.equals(username)) {
                    userModel.addElement(user.trim());
                }
            }
        });
    }

    private void showPrivateMessage(Message msg) {
        if (msg.getSender() == null) return;

        PrivateChatWindow chat = privateChats.get(msg.getSender());
        if (chat == null) {
            chat = new PrivateChatWindow(username, msg.getSender(), client);
            privateChats.put(msg.getSender(), chat);
        }
        chat.setVisible(true);
        chat.toFront();
        chat.appendMessage(msg);
    }

    private void showGroupMessage(Message msg) {
        JOptionPane.showMessageDialog(this, "Nova mensagem em grupo de " + msg.getSender() + ": " + msg.getContent());
    }

    private void showFileMessage(Message msg) {
        JOptionPane.showMessageDialog(this, "Arquivo recebido de " + msg.getSender() + ": " + msg.getFileName());
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}
