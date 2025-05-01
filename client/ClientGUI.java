package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import common.Message;
import common.MessageType;

public class ClientGUI extends JFrame {
    private DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username;
    
    // Cores
    private final Color darkBg = new Color(30, 30, 30);
    private final Color lightText = new Color(220, 220, 220);
    private final Color accentGreen = new Color(0, 200, 100);
    private final Color headerBg = new Color(45, 45, 45);
    
    // Componentes da interface
    private JPanel mainPanel;
    private JPanel contactsPanel;
    private JPanel chatPanel;
    private JTextArea chatArea;
    private JTextField inputField;
    private String currentChat;

    public ClientGUI() {
        configureLookAndFeel();
        connect(); // Conectar primeiro para obter username antes de setupInterface()
        setupInterface();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private void setupInterface() {
        setTitle("Chat de " + username);
        setSize(360, 640);
        setLayout(new BorderLayout());
        getContentPane().setBackground(darkBg);
        setLocationRelativeTo(null);
        setResizable(true);

        // Configurar painel principal com CardLayout
        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        createContactsPanel();
        createChatPanel();

        mainPanel.add(contactsPanel, "contacts");
        mainPanel.add(chatPanel, "chat");

        add(mainPanel, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void createContactsPanel() {
        contactsPanel = new JPanel(new BorderLayout());
        contactsPanel.setBackground(darkBg);

        // Cabeçalho
        JLabel header = new JLabel("Contatos Online", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 16));
        header.setForeground(accentGreen);
        header.setBorder(new EmptyBorder(10, 0, 10, 0));
        contactsPanel.add(header, BorderLayout.NORTH);

        // Lista de usuários
        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setBackground(new Color(50, 50, 50));
        userList.setForeground(lightText);
        JScrollPane userScroll = new JScrollPane(userList);
        contactsPanel.add(userScroll, BorderLayout.CENTER);

        // Painel de botões
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        buttonPanel.setBackground(darkBg);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JButton privateBtn = createStyledButton("Chat Privado");
        privateBtn.addActionListener(e -> startPrivateChat());
        
        JButton groupBtn = createStyledButton("Criar Grupo");
        groupBtn.addActionListener(e -> createGroup());
        
        buttonPanel.add(privateBtn);
        buttonPanel.add(groupBtn);
        contactsPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(darkBg);

        // Cabeçalho do chat
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(headerBg);
        header.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        JButton backBtn = createStyledButton("← Voltar");
        backBtn.addActionListener(e -> showContactsView());
        
        JLabel titleLabel = new JLabel("", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(lightText);
        
        header.add(backBtn, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        chatPanel.add(header, BorderLayout.NORTH);

        // Área de mensagens
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(darkBg);
        chatArea.setForeground(lightText);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Painel de entrada
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        inputField = new JTextField();
        inputField.setBackground(new Color(60, 60, 60));
        inputField.setForeground(lightText);
        inputField.addActionListener(e -> sendMessage());
        
        JButton sendBtn = createStyledButton("Enviar");
        sendBtn.addActionListener(e -> sendMessage());
        
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(accentGreen);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentGreen.darker(), 2),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        button.setFocusPainted(false);
        return button;
    }

    private void connect() {
        username = JOptionPane.showInputDialog(this, "Digite seu nome:");
        if (username != null && !username.trim().isEmpty()) {
            try {
                client = new Client("127.0.0.1", 54321, username, this);
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
            showChatView(receiver);
        }
    }

    private void createGroup() {
        List<String> selectedUsers = userList.getSelectedValuesList();
        if (selectedUsers.size() >= 2) {
            String groupName = String.join(",", selectedUsers);
            showChatView(groupName);
        } else {
            showError("Selecione pelo menos 2 usuários.");
        }
    }

    private void showChatView(String title) {
        currentChat = title;
        ((JLabel) ((BorderLayout) ((JPanel) chatPanel.getComponent(0)).getLayout()).getLayoutComponent(BorderLayout.CENTER)).setText(title);
        chatArea.setText("");
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
    }

    private void showContactsView() {
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            Message msg;
            if (currentChat.contains(",")) { // Grupo
                msg = new Message(username, currentChat, text, MessageType.GROUP);
            } else { // Privado
                msg = new Message(username, currentChat, text, MessageType.PRIVATE);
            }
            client.sendMessage(msg);
            chatArea.append("[Você]: " + text + "\n");
            inputField.setText("");
        }
    }

    public void handleMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            switch (msg.getType()) {
                case USER_LIST:
                    userModel.clear();
                    Arrays.stream(msg.getContent().split(","))
                        .filter(user -> !user.trim().isEmpty() && !user.trim().equals(username))
                        .forEach(user -> userModel.addElement(user.trim()));
                    break;
                    
                case PRIVATE:
                case GROUP:
                    if (msg.getSender().equals(currentChat) || currentChat.equals(msg.getReceiver())) {
                        chatArea.append("[" + msg.getSender() + "]: " + msg.getContent() + "\n");
                    }
                    break;
                    
                case FILE:
                    JOptionPane.showMessageDialog(this, "Arquivo recebido de " + msg.getSender() + ": " + msg.getFileName());
                    break;
                    
                case TEXT:
                    JOptionPane.showMessageDialog(this, "Mensagem de " + msg.getSender() + ": " + msg.getContent());
                    break;
            }
        });
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}