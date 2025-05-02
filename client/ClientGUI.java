package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import common.Message;
import common.MessageType;

public class ClientGUI extends JFrame {
    private DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username;
    private List<String> grupos = new ArrayList<>();
    
    // Histórico de mensagens (1. Armazenamento do histórico)
    private Map<String, List<String>> historicoMensagens = new HashMap<>();
    private Map<String, Boolean> notificacoes = new HashMap<>();
    
    // Cores
    private final Color darkBg = new Color(30, 30, 30);
    private final Color lightText = new Color(220, 220, 220);
    private final Color accentGreen = new Color(0, 200, 100);
    private final Color headerBg = new Color(45, 45, 45);
    
    // Componentes
    private JPanel mainPanel;
    private JPanel contactsPanel;
    private JPanel chatPanel;
    private JTextArea chatArea;
    private JTextField inputField;
    private String currentChat;

    private class ContactListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String contact = value.toString().replace(" 🔴", "");
            
            // 3. Exibição da notificação
            if (notificacoes.getOrDefault(contact, false)) {
                label.setText(contact + " 🔴");
                label.setForeground(accentGreen);
            } else {
                label.setText(contact);
                label.setForeground(lightText);
            }
            return label;
        }
    }

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
        setResizable(false);

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

        JLabel header = new JLabel("Contatos Online", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 16));
        header.setForeground(accentGreen);
        header.setBorder(new EmptyBorder(10, 0, 10, 0));
        contactsPanel.add(header, BorderLayout.NORTH);

        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setCellRenderer(new ContactListRenderer());
        userList.setBackground(new Color(50, 50, 50));
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null && !selected.equals(username)) {
                        String contact = selected.replace(" 🔴", "");
                        // 3. Limpeza da notificação
                        notificacoes.put(contact, false);
                        atualizarListaContatos();
                        showChatView(contact);
                    }
                }
            }
        });
        
        JScrollPane userScroll = new JScrollPane(userList);
        contactsPanel.add(userScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(darkBg);
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

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
        
        JLabel infoLabel = new JLabel(
            "<html><div style='text-align: center; color: #888; font-size: 10px;'>" +
            "Dê dois cliques para iniciar um chat<br/>" +
            "Clique em '+' para criar grupo</div></html>"
        );
        
        bottomPanel.add(groupBtn, BorderLayout.NORTH);
        bottomPanel.add(infoLabel, BorderLayout.CENTER);
        contactsPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createGroup() {
        List<String> selectedUsers = userList.getSelectedValuesList();
        if (selectedUsers.size() >= 2) {
            String groupName = "🧑‍🤝‍🧑 " + String.join(", ", selectedUsers);
            if (!grupos.contains(groupName)) {
                grupos.add(groupName);
                userModel.addElement(groupName);
            }
            showChatView(groupName);
        } else {
            showError("Selecione pelo menos 2 usuários.");
        }
    }

    private void atualizarListaContatos() {
        List<String> contatosAtuais = new ArrayList<>();
        for (int i = 0; i < userModel.getSize(); i++) {
            contatosAtuais.add(userModel.getElementAt(i).replace(" 🔴", ""));
        }
        
        userModel.clear();
        contatosAtuais.forEach(userModel::addElement);
    }

    // 2. Carregamento do histórico ao abrir chat
    private void showChatView(String title) {
        currentChat = title;
        ((JLabel) ((BorderLayout) ((Container) chatPanel.getComponent(0)).getLayout()).getLayoutComponent(BorderLayout.CENTER)).setText(title);
        chatArea.setText("");
        
        // Carrega todo o histórico da conversa
        historicoMensagens.getOrDefault(title, new ArrayList<>()).forEach(chatArea::append);
        
        // Remove notificação
        notificacoes.put(title, false);
        atualizarListaContatos();
        
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
    }

    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(darkBg);

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

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(darkBg);
        chatArea.setForeground(lightText);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

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

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            Message msg;
            String formattedMessage = "[Você]: " + text + "\n";
            
            if (currentChat.contains("🧑‍🤝‍🧑")) {
                // Mensagem para grupo
                String grupo = currentChat.replace("🧑‍🤝‍🧑 ", "");
                msg = new Message(username, grupo, text, MessageType.GROUP);
                // 1. Armazena no histórico
                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(formattedMessage);
            } else {
                // Mensagem privada
                msg = new Message(username, currentChat, text, MessageType.PRIVATE);
                // 1. Armazena no histórico
                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(formattedMessage);
            }
            
            client.sendMessage(msg);
            chatArea.append(formattedMessage);
            inputField.setText("");
        }
    }

    public void handleMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            switch (msg.getType()) {
                case USER_LIST:
                    List<String> onlineUsers = new ArrayList<>(Arrays.asList(msg.getContent().split(",")));
                    onlineUsers.removeIf(user -> user.trim().isEmpty() || user.equals(username));
                    onlineUsers.addAll(grupos);
                    
                    userModel.clear();
                    onlineUsers.forEach(userModel::addElement);
                    break;
                    
                case PRIVATE:
                    String senderPriv = msg.getSender();
                    String contentPriv = "[" + senderPriv + "]: " + msg.getContent() + "\n";
                    // 1. Armazena no histórico
                    historicoMensagens.computeIfAbsent(senderPriv, k -> new ArrayList<>()).add(contentPriv);
                    
                    if (senderPriv.equals(currentChat)) {
                        chatArea.append(contentPriv);
                    } else {
                        // 3. Ativa notificação
                        notificacoes.put(senderPriv, true);
                        atualizarListaContatos();
                    }
                    break;
                    
                case GROUP:
                    String groupName = msg.getReceiver();
                    String senderGroup = msg.getSender();
                    String contentGroup = "[" + senderGroup + "]: " + msg.getContent() + "\n";
                    // 1. Armazena no histórico do grupo
                    historicoMensagens.computeIfAbsent(groupName, k -> new ArrayList<>()).add(contentGroup);
                    
                    if (groupName.equals(currentChat)) {
                        chatArea.append(contentGroup);
                    } else {
                        // 3. Ativa notificação
                        notificacoes.put(groupName, true);
                        atualizarListaContatos();
                    }
                    break;
                    
                case FILE:
                    JOptionPane.showMessageDialog(this, "Arquivo recebido de " + msg.getSender());
                    break;

                case TEXT:
                    String senderText = msg.getSender();
                    String contentText = "[" + senderText + "]: " + msg.getContent() + "\n";
                    historicoMensagens.computeIfAbsent(senderText, k -> new ArrayList<>()).add(contentText);

                    if (senderText.equals(currentChat)) {
                        chatArea.append(contentText);
                    } else {
                        notificacoes.put(senderText, true);
                        atualizarListaContatos();
                    }
                    break;
            }
        });
    }

    private void showContactsView() {
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}