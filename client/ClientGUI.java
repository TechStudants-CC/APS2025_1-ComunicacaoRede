package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import common.Message;

public class ClientGUI extends JFrame {
    private DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username;
    private Map<String, PrivateChatWindow> privateChats = new HashMap<>();

    // Cores
    private final Color darkBg = new Color(30, 30, 30);
    private final Color lightText = new Color(220, 220, 220);
    private final Color accentGreen = new Color(0, 200, 100);

    public ClientGUI() {
        configureLookAndFeel();
        setupInterface();
        connect();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private void setupInterface() {
        setTitle("Chat - Cliente");
        setSize(800, 500);
        setLayout(new GridBagLayout());
        getContentPane().setBackground(darkBg);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;

        // Painel Esquerdo (Usuários Online)
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBackground(darkBg);
        leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel usersLabel = new JLabel("Usuários Online", SwingConstants.CENTER);
        usersLabel.setFont(new Font("Arial", Font.BOLD, 14));
        usersLabel.setForeground(accentGreen);
        gbc.gridy = 0;
        leftPanel.add(usersLabel, gbc);

        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setBackground(new Color(50, 50, 50));
        userList.setForeground(lightText);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(200, 0));
        gbc.gridy = 1;
        gbc.weighty = 1;
        leftPanel.add(userScroll, gbc);

        JButton privateBtn = createStyledButton("Chat Direto");
        privateBtn.addActionListener(e -> startPrivateChat());
        JButton groupBtn = createStyledButton("Criar Grupo");
        groupBtn.addActionListener(e -> createGroup());
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        buttonPanel.setBackground(darkBg);
        buttonPanel.add(privateBtn);
        buttonPanel.add(groupBtn);
        gbc.gridy = 2;
        gbc.weighty = 0;
        leftPanel.add(buttonPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.2;
        gbc.weighty = 1;
        add(leftPanel, gbc);

        // Painel Central (Instruções)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(darkBg);
        centerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel infoLabel = new JLabel(
            "<html><div style='text-align: center;'>Selecione um usuário para chat privado<br/>ou crie um grupo</div></html>",
            SwingConstants.CENTER
        );
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        infoLabel.setForeground(lightText);
        centerPanel.add(infoLabel, BorderLayout.CENTER);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.8;
        add(centerPanel, gbc);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(accentGreen);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentGreen.darker(), 2),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        button.setFocusPainted(false);
        return button;
    }

    private void connect() {
        username = JOptionPane.showInputDialog(this, "Digite seu nome:");
        if (username != null && !username.trim().isEmpty()) {
            try {
                client = new Client("127.0.0.1", 54321, username, this);
                setTitle("Chat - " + username);
            } catch (IOException e) {
                showError("Falha na conexão.");
                System.exit(1);
            }
        } else {
            showError("Nome inválido.");
            System.exit(1);
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
        }
    }

    private void createGroup() {
        java.util.List<String> selectedUsers = userList.getSelectedValuesList();
        if (selectedUsers.size() >= 2) {
            new GroupChatWindow(username, selectedUsers, client);
        } else {
            showError("Selecione pelo menos 2 usuários.");
        }
    }

    public void handleMessage(Message msg) {
        switch (msg.getType()) {
            case USER_LIST:
                SwingUtilities.invokeLater(() -> {
                    userModel.clear();
                    Arrays.stream(msg.getContent().split(","))
                        .filter(user -> !user.trim().isEmpty() && !user.trim().equals(username))
                        .forEach(user -> userModel.addElement(user.trim()));
                });
                break;
            case PRIVATE:
                showPrivateMessage(msg);
                break;
            case GROUP:
                JOptionPane.showMessageDialog(this, "Nova mensagem no grupo de " + msg.getSender());
                break;
            case FILE:
                JOptionPane.showMessageDialog(this, "Arquivo recebido de " + msg.getSender() + ": " + msg.getFileName());
                break;
            case TEXT:
                JOptionPane.showMessageDialog(this, "Mensagem de " + msg.getSender() + ": " + msg.getContent());
                break;
        }
    }

    private void showPrivateMessage(Message msg) {
        PrivateChatWindow chat = privateChats.get(msg.getSender());
        if (chat == null) {
            chat = new PrivateChatWindow(username, msg.getSender(), client);
            privateChats.put(msg.getSender(), chat);
        }
        chat.appendMessage(msg);
        chat.setVisible(true);
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}