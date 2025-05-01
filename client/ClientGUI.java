package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
        connect();
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
        setResizable(false); // Janela não redimensionável

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

        // Lista de usuários com duplo clique
        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setBackground(new Color(50, 50, 50));
        userList.setForeground(lightText);
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) { // Duplo clique
                    startPrivateChat();
                }
            }
        });
        JScrollPane userScroll = new JScrollPane(userList);
        contactsPanel.add(userScroll, BorderLayout.CENTER);

        // Painel inferior com botão e instruções
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(darkBg);
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Botão de criar grupo
        JButton groupBtn = new JButton("+");
        groupBtn.setBackground(accentGreen);
        groupBtn.setForeground(Color.WHITE);
        groupBtn.setFont(new Font("Arial", Font.BOLD, 20));
        groupBtn.setPreferredSize(new Dimension(60, 40));
        groupBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentGreen.darker(), 2),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        groupBtn.addActionListener(e -> createGroup());
        
        // Texto de instrução
        JLabel infoLabel = new JLabel(
            "<html><div style='text-align: center; color: #888; font-size: 10px;'>" +
            "Dê dois cliques no nome de um usuário para iniciar um chat privado.<br/>" +
            "Clique no botão abaixo para criar um grupo." +
            "</div></html>"
        );
        
        bottomPanel.add(groupBtn, BorderLayout.NORTH);
        bottomPanel.add(infoLabel, BorderLayout.CENTER);
        contactsPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(darkBg);

        // Cabeçalho do chat
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(headerBg);
        header.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        JButton backBtn = new JButton("← Voltar");
        backBtn.setBackground(new Color(70, 70, 70));
        backBtn.setForeground(lightText);
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
        
        JButton sendBtn = new JButton("Enviar");
        sendBtn.setBackground(accentGreen);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.addActionListener(e -> sendMessage());
        
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
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