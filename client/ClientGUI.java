package client;

import common.Message;
import common.MessageStatus;
import common.MessageType;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public class ClientGUI extends JFrame {
    private final DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username; // Será definido após o login bem-sucedido
    private final AuthManager authManager;

    private final List<String> gruposParticipando = new ArrayList<>();
    private final Map<String, List<Message>> historicoMensagens = new HashMap<>();
    private final Map<String, Boolean> notificacoes = new HashMap<>();
    private final Map<String, JPanel> messagePanels = new HashMap<>();

    // Cores e Constantes
    private final Color primaryColor = new Color(7, 94, 84);
    private final Color secondaryColor = new Color(37, 211, 102);
    private final Color accentColor = new Color(18, 140, 126);
    private final Color background = new Color(236, 229, 221);
    private final Color dialogBackground = new Color(248, 248, 248);
    private final Color chatPanelBackground = new Color(224, 237, 232);
    private final Color listBackground = Color.WHITE;
    private final Color mutedTextColor = new Color(100, 100, 100);
    private final Color sentMessageColor = new Color(220, 248, 198);
    private final Color receivedMessageColor = Color.WHITE;
    private final Color readStatusColor = new Color(52, 183, 241);
    private final Color systemMessageColor = new Color(255, 252, 213);
    private final Color systemMessageTextColor = new Color(80, 80, 80);
    private final int MESSAGE_BOTTOM_MARGIN = 8;

    // Componentes da UI
    private JPanel mainPanel;
    private JPanel contactsPanel;
    private JPanel chatPanel;
    private JPanel chatMessagesPanel;
    private JScrollPane chatScrollPane;
    private JTextField inputField;
    private JButton btnLeaveGroup;
    private JLabel chatTitleLabel;
    private String currentChat;

    private boolean isInChatView = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    public static final String NOTIFICATION_ICON = " \uD83D\uDD34";
    public static final String GROUP_ICON_PREFIX = "\uD83D\uDC65 ";


    public ClientGUI() {
        this.authManager = new AuthManager();
        this.userModel = new DefaultListModel<>();

        setTitle("ChatApp");
        setSize(375, 700);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Alterado para controlar o fechamento
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (client != null) {
                    client.stopClient(); // Para o cliente de forma limpa
                }
                System.out.println("Encerrando aplicação ChatApp.");
                System.exit(0); // Encerra a aplicação
            }
        });
        setLocationRelativeTo(null);
        getContentPane().setBackground(background);

        showLoginScreen();
    }

    // Método Getter para username, usado por Client.java
    public String getUsername() {
        return this.username;
    }

    private void showLoginScreen() {
        final JDialog loginDialog = new JDialog(this, "Chat APS - Entrar", true); // loginDialog precisa ser final ou effectively final
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (username == null || username.isEmpty()) {
                    System.err.println("Login não concluído. Aplicação será encerrada.");
                    System.exit(0);
                }
            }
        });

        JPanel dialogMainPanel = new JPanel(new GridBagLayout());
        dialogMainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        dialogMainPanel.setBackground(dialogBackground);
        loginDialog.setContentPane(dialogMainPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel mainTitleLabel = new JLabel("Chat Ambiental APS");
        mainTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        mainTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainTitleLabel.setForeground(accentColor);
        gbc.gridwidth = 2;
        dialogMainPanel.add(mainTitleLabel, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(15, 5, 5, 5);

        JLabel userLabel = new JLabel("Usuário:");
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JTextField userField = new JTextField(20); // userField precisa ser final ou effectively final
        userField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(userField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        JLabel passLabel = new JLabel("Senha:");
        passLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(passLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JPasswordField passField = new JPasswordField(20); // passField precisa ser final ou effectively final
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(passField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel ipLabel = new JLabel("IP Servidor:");
        ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(ipLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JTextField serverIpField = new JTextField(20); // serverIpField precisa ser final ou effectively final
        serverIpField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        serverIpField.setText("127.0.0.1");
        dialogMainPanel.add(serverIpField, gbc);


        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 5, 5, 5);
        JButton loginButton = new JButton("Entrar");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        loginButton.setBackground(secondaryColor);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginButton.setPreferredSize(new Dimension(140, 45));
        dialogMainPanel.add(loginButton, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 5, 10, 5);
        JLabel registerLink = new JLabel("<html><a href=''>Não tem conta? Registrar</a></html>");
        registerLink.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        registerLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerLink.setHorizontalAlignment(SwingConstants.CENTER);
        registerLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                loginDialog.setVisible(false);
                showRegistrationScreen(loginDialog); // Passa loginDialog para reexibir se necessário
            }
        });
        dialogMainPanel.add(registerLink, gbc);

        @SuppressWarnings("unused")
        ActionListener loginAction = event -> {
            String inputUser = userField.getText().trim();
            String inputPass = new String(passField.getPassword());
            String serverIpText = serverIpField.getText().trim();

            if (inputUser.isEmpty() || inputPass.isEmpty() || serverIpText.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, "Usuário, senha e IP do servidor são obrigatórios!", "Entrada Inválida", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (authManager.loginUser(inputUser, inputPass)) {
                try {
                    ClientGUI.this.username = inputUser; // Define o username da GUI
                    // 1. Cria o cliente
                    ClientGUI.this.client = new Client(serverIpText, 54321, ClientGUI.this.username, ClientGUI.this);
                    // 2. Inicia a thread de escuta do cliente APÓS a construção completa
                    ClientGUI.this.client.startListening();

                    loginDialog.dispose();
                    setupInterface();
                    setTitle("ChatApp - " + ClientGUI.this.username);
                    setVisible(true);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(loginDialog, "Falha na conexão com o servidor: " + ex.getMessage(), "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                    ClientGUI.this.username = null;
                    ClientGUI.this.client = null; 
                }
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Usuário ou senha inválidos.", "Falha no Login", JOptionPane.ERROR_MESSAGE);
            }
        };

        userField.addActionListener(loginAction);
        passField.addActionListener(loginAction);
        serverIpField.addActionListener(loginAction);
        loginButton.addActionListener(loginAction);

        loginDialog.pack();
        loginDialog.setLocationRelativeTo(this); // 'this' aqui é o JFrame principal ClientGUI
        loginDialog.setVisible(true);
    }

    private void showRegistrationScreen(final JDialog parentLoginDialog) { // parentLoginDialog precisa ser final ou effectively final
        final JDialog registerDialog = new JDialog(this, "Registrar Novo Usuário", true); // registerDialog precisa ser final ou effectively final
        registerDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        registerDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                parentLoginDialog.setVisible(true);
            }
        });

        JPanel dialogMainPanel = new JPanel(new GridBagLayout());
        dialogMainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        dialogMainPanel.setBackground(dialogBackground);
        registerDialog.setContentPane(dialogMainPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        // ... (configurações do GridBagLayout como antes) ...
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel mainTitleLabel = new JLabel("Criar Nova Conta");
        mainTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        mainTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainTitleLabel.setForeground(accentColor);
        gbc.gridwidth = 2;
        dialogMainPanel.add(mainTitleLabel, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(15, 5, 5, 5);

        JLabel userLabel = new JLabel("Nome de Usuário:");
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JTextField userField = new JTextField(20); // userField precisa ser final ou effectively final
        userField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(userField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        JLabel passLabel = new JLabel("Senha:");
        passLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(passLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JPasswordField passField = new JPasswordField(20); // passField precisa ser final ou effectively final
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(passField, gbc);
        
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel confirmPassLabel = new JLabel("Confirmar Senha:");
        confirmPassLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(confirmPassLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        final JPasswordField confirmPassField = new JPasswordField(20); // confirmPassField precisa ser final ou effectively final
        confirmPassField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(confirmPassField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 5, 10, 5);
        JButton registerButton = new JButton("Registrar");
        // ... (configurações do botão como antes) ...
        registerButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        registerButton.setBackground(secondaryColor);
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        registerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerButton.setPreferredSize(new Dimension(140, 45));
        dialogMainPanel.add(registerButton, gbc);


        @SuppressWarnings("unused")
        ActionListener registerAction = event -> {
            String inputUser = userField.getText().trim();
            String inputPass = new String(passField.getPassword());
            String inputConfirmPass = new String(confirmPassField.getPassword());

            if (inputUser.isEmpty() || inputPass.isEmpty()) {
                JOptionPane.showMessageDialog(registerDialog, "Nome de usuário e senha são obrigatórios!", "Entrada Inválida", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!inputPass.equals(inputConfirmPass)) {
                JOptionPane.showMessageDialog(registerDialog, "As senhas não coincidem!", "Erro de Senha", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (inputPass.length() < 6) {
                 JOptionPane.showMessageDialog(registerDialog, "A senha deve ter pelo menos 6 caracteres.", "Senha Fraca", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (authManager.registerUser(inputUser, inputPass)) {
                JOptionPane.showMessageDialog(registerDialog, "Usuário '" + inputUser + "' registrado com sucesso!\nFaça o login para continuar.", "Registro Concluído", JOptionPane.INFORMATION_MESSAGE);
                registerDialog.dispose();
                parentLoginDialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(registerDialog, "Falha no registro. O nome de usuário pode já existir ou ocorreu um erro no banco de dados.", "Erro de Registro", JOptionPane.ERROR_MESSAGE);
            }
        };
        userField.addActionListener(registerAction);
        passField.addActionListener(registerAction);
        confirmPassField.addActionListener(registerAction);
        registerButton.addActionListener(registerAction);

        registerDialog.pack();
        registerDialog.setLocationRelativeTo(this); // 'this' aqui é o JFrame principal ClientGUI
        registerDialog.setVisible(true);
    }

    private void setupInterface() {
        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(background);

        createContactsPanel();
        createChatPanel();

        mainPanel.add(contactsPanel, "contacts");
        mainPanel.add(chatPanel, "chat");

        add(mainPanel, BorderLayout.CENTER);
    }

    private static class ContactListRenderer extends DefaultListCellRenderer {
        private static final Color LIST_BACKGROUND_RENDERER = Color.WHITE;
        private static final Color TEXT_COLOR_RENDERER = Color.BLACK;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String contactName = value.toString();
            boolean hasNotification = contactName.endsWith(NOTIFICATION_ICON);

            if (hasNotification) {
                contactName = contactName.substring(0, contactName.length() - NOTIFICATION_ICON.length()).trim();
            }

            label.setText(contactName);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            label.setBorder(new EmptyBorder(10, 15, 10, 15));
            label.setOpaque(true);

            if (hasNotification) {
                label.setFont(new Font("Segoe UI", Font.BOLD, 16));
                label.setForeground(Color.RED.darker());
            } else {
                label.setForeground(TEXT_COLOR_RENDERER);
            }

            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(LIST_BACKGROUND_RENDERER);
            }
            return label;
        }
    }

    @SuppressWarnings("unused")
    private void createContactsPanel() {
        contactsPanel = new JPanel(new BorderLayout(0, 10));
        contactsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        contactsPanel.setBackground(listBackground);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(primaryColor);
        headerPanel.setPreferredSize(new Dimension(0, 60));
        headerPanel.setBorder(new EmptyBorder(10,15,10,15));

        JLabel titleLabel = new JLabel("Conversas");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        JButton btnNewGroup = new JButton(GROUP_ICON_PREFIX + "+");
        btnNewGroup.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16));
        btnNewGroup.setToolTipText("Criar novo grupo");
        btnNewGroup.setForeground(Color.WHITE);
        btnNewGroup.setBackground(accentColor);
        btnNewGroup.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
        btnNewGroup.setFocusPainted(false);
        btnNewGroup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnNewGroup.addActionListener(e -> createGroup());
        headerPanel.add(btnNewGroup, BorderLayout.EAST);

        contactsPanel.add(headerPanel, BorderLayout.NORTH);

        userList = new JList<>(userModel);
        userList.setCellRenderer(new ContactListRenderer());
        userList.setBackground(listBackground);
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedValue = userList.getSelectedValue();
                    if (selectedValue != null) {
                        String contactName = selectedValue.replace(NOTIFICATION_ICON, "").trim();
                        if (username != null && !contactName.equals(username)) { // Adicionado null check para username
                            notificacoes.put(contactName, false);
                            showChatView(contactName);
                            atualizarListaContatosComNotificacao();
                        }
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1,0,0,0, new Color(220,220,220)));
        contactsPanel.add(scrollPane, BorderLayout.CENTER);
    }

    @SuppressWarnings("unused")
    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout(0,0));
        chatPanel.setBackground(background);

        JPanel headerChatPanel = new JPanel(new BorderLayout());
        headerChatPanel.setBackground(primaryColor);
        headerChatPanel.setPreferredSize(new Dimension(0, 60));
        headerChatPanel.setBorder(new EmptyBorder(5,10,5,10));

        JButton btnBack = new JButton("←");
        btnBack.setFont(new Font("Arial", Font.BOLD, 24));
        btnBack.setForeground(Color.WHITE);
        btnBack.setOpaque(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setBorderPainted(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> showContactsView());
        headerChatPanel.add(btnBack, BorderLayout.WEST);

        chatTitleLabel = new JLabel("", SwingConstants.LEFT);
        chatTitleLabel.setName("chatTitleLabel");
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        chatTitleLabel.setForeground(Color.WHITE);
        chatTitleLabel.setBorder(new EmptyBorder(0, 15, 0, 0));
        chatTitleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chatTitleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentChat != null && currentChat.startsWith(GROUP_ICON_PREFIX)) {
                    requestGroupInfo(currentChat);
                }
            }
        });
        headerChatPanel.add(chatTitleLabel, BorderLayout.CENTER);


        btnLeaveGroup = new JButton("Sair do Grupo");
        btnLeaveGroup.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLeaveGroup.setForeground(Color.WHITE);
        btnLeaveGroup.setBackground(accentColor.darker());
        btnLeaveGroup.setToolTipText("Deixar este grupo");
        btnLeaveGroup.setFocusPainted(false);
        btnLeaveGroup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLeaveGroup.setVisible(false);
        btnLeaveGroup.addActionListener(e -> leaveGroup());

        JPanel eastHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        eastHeaderPanel.setOpaque(false);
        eastHeaderPanel.add(btnLeaveGroup);
        headerChatPanel.add(eastHeaderPanel, BorderLayout.EAST);

        chatPanel.add(headerChatPanel, BorderLayout.NORTH);

        chatMessagesPanel = new JPanel();
        chatMessagesPanel.setLayout(new BoxLayout(chatMessagesPanel, BoxLayout.Y_AXIS));
        chatMessagesPanel.setBackground(chatPanelBackground);
        chatMessagesPanel.setBorder(new EmptyBorder(MESSAGE_BOTTOM_MARGIN, 10, 10, 10));


        chatScrollPane = new JScrollPane(chatMessagesPanel);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(background);
        inputPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JButton btnAttach = createIconButton("\uD83D\uDCCE");
        btnAttach.setToolTipText("Anexar arquivo");
        btnAttach.addActionListener(e -> sendFile());

        JPanel attachButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0,0));
        attachButtonPanel.setOpaque(false);
        attachButtonPanel.add(btnAttach);
        attachButtonPanel.setBorder(new EmptyBorder(0,0,0,5));


        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        inputField.setBorder(new RoundBorder(25, Color.WHITE, true));
        inputField.setBackground(Color.WHITE);
        inputField.addActionListener(e -> sendMessage());

        JPanel textAndAttachPanel = new JPanel(new BorderLayout());
        textAndAttachPanel.setOpaque(false);
        textAndAttachPanel.add(attachButtonPanel, BorderLayout.WEST);
        textAndAttachPanel.add(inputField, BorderLayout.CENTER);

        inputPanel.add(textAndAttachPanel, BorderLayout.CENTER);


        JButton btnSend = new JButton("\u27A4");
        btnSend.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
        btnSend.setBackground(secondaryColor);
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setBorder(new RoundBorder(25, secondaryColor, false));
        btnSend.setPreferredSize(new Dimension(50, 50));
        btnSend.addActionListener(e -> sendMessage());
        inputPanel.add(btnSend, BorderLayout.EAST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private void requestGroupInfo(String groupNameWithIcon) {
        if (client != null) {
            client.sendMessage(new Message(getUsername(), groupNameWithIcon, "REQUEST_INFO", MessageType.GROUP_INFO_REQUEST));
        }
    }

    private void showGroupInfoDialog(String groupNameWithIcon, String membersString) {
        String cleanGroupName = groupNameWithIcon.replace(GROUP_ICON_PREFIX, "").trim();
        JDialog groupInfoDialog = new JDialog(this, "Info do Grupo: " + cleanGroupName, true);
        groupInfoDialog.setSize(300, 400);
        groupInfoDialog.setLocationRelativeTo(this);
        groupInfoDialog.setLayout(new BorderLayout(10, 10));
        groupInfoDialog.getContentPane().setBackground(background);

        JLabel title = new JLabel(cleanGroupName, SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(primaryColor);
        title.setBorder(new EmptyBorder(10,10,0,10));
        groupInfoDialog.add(title, BorderLayout.NORTH);

        String[] membersArray = membersString.split(",");
        DefaultListModel<String> membersListModel = new DefaultListModel<>();
        for (String member : membersArray) {
            membersListModel.addElement(member.trim());
        }
        JList<String> membersJList = new JList<>(membersListModel);
        membersJList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        membersJList.setBorder(new EmptyBorder(5,15,5,15));
        membersJList.setBackground(listBackground);

        JScrollPane scrollPane = new JScrollPane(membersJList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(new EmptyBorder(5,15,15,15));
        listPanel.setBackground(background);
        listPanel.add(new JLabel("Membros (" + membersArray.length + "):"), BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        groupInfoDialog.add(listPanel, BorderLayout.CENTER);
        groupInfoDialog.setVisible(true);
    }


    private JButton createIconButton(String unicodeIcon) {
        JButton button = new JButton(unicodeIcon);
        button.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(mutedTextColor);
        return button;
    }

    private void addSystemMessageToPanel(String content) {
        JPanel systemMessageRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        systemMessageRowPanel.setBackground(chatPanelBackground);
        systemMessageRowPanel.setBorder(BorderFactory.createEmptyBorder(MESSAGE_BOTTOM_MARGIN / 2, 0, MESSAGE_BOTTOM_MARGIN, 0));

        JPanel bubblePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bubblePanel.setBorder(new CompoundBorder(
                new RoundBorder(10, systemMessageColor, false),
                new EmptyBorder(5, 10, 5, 10)
        ));
        bubblePanel.setBackground(systemMessageColor);

        JTextArea messageText = new JTextArea(content);
        messageText.setEditable(false);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        messageText.setForeground(systemMessageTextColor);
        messageText.setOpaque(false);

        int chatAreaWidth = chatScrollPane.getViewport().getWidth() > 0 ? chatScrollPane.getViewport().getWidth() : chatMessagesPanel.getWidth();
        if(chatAreaWidth <= 0) chatAreaWidth = (int)(this.getWidth() * 0.8);
        messageText.setMaximumSize(new Dimension((int)(chatAreaWidth * 0.85), Short.MAX_VALUE));

        bubblePanel.add(messageText);
        systemMessageRowPanel.add(bubblePanel);

        chatMessagesPanel.add(systemMessageRowPanel);
        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
        scrollToBottom();
    }


    @SuppressWarnings("unused")
    private void addMessageToPanel(Message msg, boolean isOwnMessage) {
        JPanel messageRowPanel = new JPanel(new FlowLayout(isOwnMessage ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        messageRowPanel.setBackground(chatPanelBackground);
        messageRowPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, MESSAGE_BOTTOM_MARGIN, 0));


        JPanel bubbleWrapper = new JPanel();
        bubbleWrapper.setLayout(new BoxLayout(bubbleWrapper, BoxLayout.X_AXIS));
        bubbleWrapper.setBackground(chatPanelBackground);

        JPanel bubblePanel = new JPanel(new BorderLayout(5, 3));
        bubblePanel.setBorder(new CompoundBorder(
                new RoundBorder(15, isOwnMessage ? sentMessageColor : receivedMessageColor, false),
                new EmptyBorder(8, 12, 5, 12)
        ));
        bubblePanel.setBackground(isOwnMessage ? sentMessageColor : receivedMessageColor);

        String messageContentToDisplay = msg.getContent();
        if (msg.getType() == MessageType.GROUP && !isOwnMessage && msg.getSender() != null && !msg.getSender().isEmpty()) {
             if (getUsername() != null && !msg.getSender().equals(getUsername())) { // Usar getUsername()
                if (!messageContentToDisplay.startsWith(msg.getSender() + ":\n")) {
                     messageContentToDisplay = msg.getSender() + ":\n" + msg.getContent();
                }
            }
        }


        JTextArea messageText = new JTextArea(messageContentToDisplay);
        messageText.setEditable(false);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        messageText.setOpaque(false);
        bubblePanel.add(messageText, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerPanel.setOpaque(false);

        JLabel timeLabel = new JLabel(timeFormat.format(msg.getTimestamp()));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(mutedTextColor);
        footerPanel.add(timeLabel);

        if (isOwnMessage) {
            JLabel statusLabel = new JLabel(getStatusIcon(msg.getStatus()));
            statusLabel.setName("statusLabel_" + msg.getMessageId());
            statusLabel.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
            statusLabel.setForeground(msg.getStatus() == MessageStatus.READ ? readStatusColor : mutedTextColor);
            footerPanel.add(statusLabel);
        } else if (msg.getFileData() != null && msg.getFileName() != null) {
            JButton btnDownloadFile = new JButton("\uD83D\uDCE5");
            btnDownloadFile.setToolTipText("Baixar " + msg.getFileName());
            btnDownloadFile.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
            btnDownloadFile.setMargin(new Insets(2,2,2,2));
            btnDownloadFile.setFocusPainted(false);
            btnDownloadFile.addActionListener(e -> downloadFile(msg.getFileName(), msg.getFileData()));
            footerPanel.add(btnDownloadFile);
        }

        bubblePanel.add(footerPanel, BorderLayout.SOUTH);

        bubbleWrapper.add(bubblePanel);
        if (isOwnMessage) {
            messageRowPanel.add(Box.createHorizontalGlue());
            messageRowPanel.add(bubbleWrapper);
        } else {
            messageRowPanel.add(bubbleWrapper);
            messageRowPanel.add(Box.createHorizontalGlue());
        }

        int chatAreaWidth = chatScrollPane.getViewport().getWidth() > 0 ? chatScrollPane.getViewport().getWidth() : chatMessagesPanel.getWidth();
        if(chatAreaWidth <= 0) chatAreaWidth = (int)(this.getWidth() * 0.8);
        bubbleWrapper.setMaximumSize(new Dimension((int)(chatAreaWidth * 0.75), Integer.MAX_VALUE));

        chatMessagesPanel.add(messageRowPanel);
        messagePanels.put(msg.getMessageId(), bubblePanel);

        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
        scrollToBottom();
    }

    private void downloadFile(String fileName, byte[] fileData) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Salvar arquivo como...");
        fileChooser.setSelectedFile(new File(fileName));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try {
                Files.write(fileToSave.toPath(), fileData);
                JOptionPane.showMessageDialog(this,
                        "Arquivo salvo com sucesso em:\n" + fileToSave.getAbsolutePath(),
                        "Download Concluído",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                showError("Erro ao salvar o arquivo: " + ex.getMessage());
            }
        }
    }

    private void updateMessageStatusOnGUI(String messageId, MessageStatus newStatus, Date relevantTime) {
        JPanel bubblePanel = messagePanels.get(messageId);
        if (bubblePanel != null) {
            // Usando instanceof com pattern matching (Java 16+)
            if (bubblePanel.getComponent(1) instanceof JPanel footerPanel) { // Assumindo que o footer é o componente de índice 1
                for (Component comp : footerPanel.getComponents()) {
                    // Usando instanceof com pattern matching (Java 16+)
                    if (comp instanceof JLabel statusLabel && comp.getName() != null && comp.getName().equals("statusLabel_" + messageId)) {
                        statusLabel.setText(getStatusIcon(newStatus));
                        statusLabel.setForeground(newStatus == MessageStatus.READ ? readStatusColor : mutedTextColor);
                        break;
                    }
                }
            }
            historicoMensagens.values().stream()
                .flatMap(List::stream)
                .filter(m -> m.getMessageId().equals(messageId))
                .findFirst()
                .ifPresent(msgToUpdate -> {
                    msgToUpdate.setStatus(newStatus);
                    if (newStatus == MessageStatus.DELIVERED && relevantTime != null) msgToUpdate.setDeliveredTime(relevantTime);
                    if (newStatus == MessageStatus.READ && relevantTime != null) msgToUpdate.setReadTime(relevantTime);
                });

            bubblePanel.revalidate();
            bubblePanel.repaint();
        }
    }

    private String getStatusIcon(MessageStatus status) {
        // Usando "rule switch" (Java 14+)
        MessageStatus currentStatus = (status == null) ? MessageStatus.SENDING : status; // Evita NullPointerException
        return switch (currentStatus) {
            case SENDING -> "⏳";
            case SENT -> "✓";
            case DELIVERED, READ -> "✓✓"; // READ usa a mesma iconografia, mas cor diferente
            case FAILED -> "✗";
        };
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            if (chatScrollPane != null) {
                JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
                if (vertical != null) {
                     vertical.setValue(vertical.getMaximum());
                }
            }
            if (chatMessagesPanel != null) {
                chatMessagesPanel.revalidate();
                chatMessagesPanel.repaint();
            }
        });
    }

    private void showContactsView() {
        isInChatView = false;
        currentChat = null;
        if(btnLeaveGroup != null) btnLeaveGroup.setVisible(false);
        if (mainPanel != null) {
            ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
        }
    }

    private void showChatView(String contactOrGroupName) {
        currentChat = contactOrGroupName;
        isInChatView = true;

        if(chatTitleLabel != null) {
            chatTitleLabel.setText(contactOrGroupName.replace(NOTIFICATION_ICON, "").trim());
        }

        if (btnLeaveGroup != null) {
            btnLeaveGroup.setVisible(contactOrGroupName.startsWith(GROUP_ICON_PREFIX));
        }

        if (chatMessagesPanel != null) {
            chatMessagesPanel.removeAll();
        }
        messagePanels.clear();

        @SuppressWarnings("unused")
        List<Message> historico = historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>());
        for (Message msg : historico) {
            if (msg.getType() == MessageType.GROUP_SYSTEM_MESSAGE) {
                addSystemMessageToPanel(msg.getContent());
            } else {
                boolean isOwn = getUsername() != null && msg.getSender().equals(getUsername()); // Usar getUsername()
                addMessageToPanel(msg, isOwn);
                if (!isOwn && msg.getStatus() != MessageStatus.READ &&
                    msg.getType() != MessageType.STATUS_UPDATE &&
                    msg.getType() != MessageType.GROUP_SYSTEM_MESSAGE &&
                    msg.getType() != MessageType.MESSAGE_READ) {
                    if (client != null) {
                        client.sendMessage(new Message(msg.getMessageId(), getUsername(), msg.getSender(), "READ_CONFIRMATION", MessageType.MESSAGE_READ));
                    }
                }
            }
        }

        notificacoes.put(currentChat, false);
        atualizarListaContatosComNotificacao();
        if (mainPanel != null) {
            ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
        }
        scrollToBottom();
        if (inputField != null) {
            inputField.requestFocusInWindow();
        }
    }

    private void createGroup() {
        if (userList == null) return;
        List<String> selectedUsersOnList = userList.getSelectedValuesList();
        if (selectedUsersOnList == null || selectedUsersOnList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione pelo menos um usuário (além de você) para criar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> membersToInvite = new ArrayList<>();
        for (String selected : selectedUsersOnList) {
            String cleanName = selected.replace(NOTIFICATION_ICON, "").trim().replace(GROUP_ICON_PREFIX,"").trim();
            if (getUsername() != null && !cleanName.equals(getUsername()) && !cleanName.startsWith(GROUP_ICON_PREFIX)) { // Usar getUsername()
                membersToInvite.add(cleanName);
            }
        }

        if (membersToInvite.isEmpty()){
            JOptionPane.showMessageDialog(this, "Selecione outros usuários válidos (diferentes de você e que não sejam grupos) para formar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String groupNameInput = JOptionPane.showInputDialog(this, "Digite o nome do grupo:", "Novo Grupo", JOptionPane.PLAIN_MESSAGE);
        if (groupNameInput == null || groupNameInput.trim().isEmpty()) {
            return;
        }
        String fullGroupName = GROUP_ICON_PREFIX + groupNameInput.trim();

        for (int i = 0; i < userModel.getSize(); i++) {
            if (userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim().equals(fullGroupName)) {
                 JOptionPane.showMessageDialog(this, "Um grupo com este nome já existe na sua lista.", "Erro", JOptionPane.ERROR_MESSAGE);
                 return;
            }
        }

        List<String> allMembersForServer = new ArrayList<>(membersToInvite);
        if (getUsername() != null) { // Usar getUsername()
            allMembersForServer.add(getUsername());
        } else {
            showError("Nome de usuário não definido. Não é possível criar grupo.");
            return;
        }


        String groupCreationContent = fullGroupName + ";" + String.join(",", allMembersForServer);
        Message groupCreateMsg = new Message(getUsername(), "Servidor", groupCreationContent, MessageType.GROUP_CREATE);
        if (client != null) client.sendMessage(groupCreateMsg);
    }

    private void leaveGroup() {
        if (currentChat != null && currentChat.startsWith(GROUP_ICON_PREFIX)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Tem certeza que deseja sair do grupo '" + currentChat.replace(GROUP_ICON_PREFIX, "") + "'?",
                    "Sair do Grupo",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                Message leaveGroupMsg = new Message(getUsername(), currentChat, "LEAVE_REQUEST", MessageType.LEAVE_GROUP); // Usar getUsername()
                if (client != null) client.sendMessage(leaveGroupMsg);
            }
        }
    }

    @SuppressWarnings("unused")
    private void sendMessage() {
        if (inputField == null || currentChat == null || getUsername() == null) return; // Usar getUsername()
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            MessageType type = currentChat.startsWith(GROUP_ICON_PREFIX) ? MessageType.GROUP : MessageType.PRIVATE;
            String receiver = currentChat;

            Message msg = new Message(getUsername(), receiver, text, type);
            msg.setStatus(MessageStatus.SENDING);

            if (client != null) client.sendMessage(msg);

            historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(msg);
            addMessageToPanel(msg, true);
            inputField.setText("");
            scrollToBottom();
        }
    }

    @SuppressWarnings("unused")
    private void sendFile() {
        if (currentChat == null) {
            showError("Selecione uma conversa para enviar o arquivo.");
            return;
        }
        if (getUsername() == null) { // Usar getUsername()
             showError("Nome de usuário não definido. Não é possível enviar arquivo.");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecione um arquivo para enviar");
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileData = Files.readAllBytes(file.toPath());
                String fileName = file.getName();
                if (fileData.length > 20 * 1024 * 1024) {
                    showError("O arquivo é muito grande (limite de 20MB).");
                    return;
                }

                MessageType type = currentChat.startsWith(GROUP_ICON_PREFIX) ? MessageType.GROUP : MessageType.PRIVATE;
                String receiver = currentChat;
                String fileMessageContent = "Arquivo: " + fileName + " (" + fileData.length / 1024 + " KB)";

                Message fileMessage = new Message(getUsername(), receiver, fileMessageContent, type);
                fileMessage.setFileData(fileData);
                fileMessage.setFileName(fileName);
                fileMessage.setStatus(MessageStatus.SENDING);

                if (client != null) client.sendMessage(fileMessage);

                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(fileMessage);
                addMessageToPanel(fileMessage, true);
                scrollToBottom();

            } catch (IOException e) {
                showError("Erro ao ler o arquivo: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unused")
    public void handleMessage(Message msg) {
        // A chamada a SwingUtilities.invokeLater já é feita pelo Client.java
        // ao chamar este método, então não é necessário repetir aqui.

        String currentGuiUsername = getUsername(); // Obter uma vez para consistência na execução do método

        MessageType type = msg.getType();
        if (type == null) {
             System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] WARN: Tipo de mensagem nulo recebido.");
             return;
        }

        switch (type) {
            case USER_LIST -> {
                System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] DEBUG: Recebeu USER_LIST: " + msg.getContent());
                List<String> receivedItemsFromServer = new ArrayList<>(Arrays.asList(msg.getContent().split(",")));
                String previouslySelectedContact = (userList != null) ? userList.getSelectedValue() : null;

                this.gruposParticipando.clear();
                for (String item : receivedItemsFromServer) {
                    if (item.startsWith(GROUP_ICON_PREFIX) && !item.trim().isEmpty()) {
                         this.gruposParticipando.add(item.trim());
                    }
                }

                userModel.clear();
                Set<String> uniqueItemsFromUserList = new LinkedHashSet<>();
                for (String itemFromServer : receivedItemsFromServer) {
                    if (itemFromServer != null && !itemFromServer.trim().isEmpty()) {
                        uniqueItemsFromUserList.add(itemFromServer.replace(NOTIFICATION_ICON, "").trim());
                    }
                }
                 for (String uniqueItem : uniqueItemsFromUserList) {
                    userModel.addElement(uniqueItem);
                }

                atualizarListaContatosComNotificacao();

                if (previouslySelectedContact != null && userList != null) {
                    boolean found = false;
                    String cleanSelectedValue = previouslySelectedContact.replace(NOTIFICATION_ICON, "").trim();
                    for (int i = 0; i < userModel.getSize(); i++) {
                        if (userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim().equals(cleanSelectedValue)) {
                            userList.setSelectedValue(userModel.getElementAt(i), true);
                            found = true;
                            break;
                        }
                    }
                    if (!found) userList.clearSelection();
                } else if (userList != null) {
                    userList.clearSelection();
                }
            }
            case PRIVATE, GROUP -> {
                boolean isOwnMessage = currentGuiUsername != null && msg.getSender().equals(currentGuiUsername);
                String chatKey;

                if (msg.getType() == MessageType.GROUP) {
                    chatKey = msg.getReceiver();
                } else {
                    chatKey = isOwnMessage ? msg.getReceiver() : msg.getSender();
                }

                if (msg.getFileData() != null && msg.getFileName() != null && !isOwnMessage) {
                    msg.setContent("Arquivo: " + msg.getFileName() + " (" + msg.getFileData().length / 1024 + " KB)");
                }

                if (!isOwnMessage) {
                    historicoMensagens.computeIfAbsent(chatKey, k -> new ArrayList<>()).add(msg);
                    if (isInChatView && currentChat != null && currentChat.equals(chatKey)) {
                        addMessageToPanel(msg, false);
                        if(msg.getType() != MessageType.STATUS_UPDATE && msg.getType() != MessageType.GROUP_SYSTEM_MESSAGE && msg.getType() != MessageType.MESSAGE_READ){
                            if (client != null && currentGuiUsername != null) client.sendMessage(new Message(msg.getMessageId(), currentGuiUsername, msg.getSender(), "READ_CONFIRMATION", MessageType.MESSAGE_READ));
                        }
                    } else {
                        notificacoes.put(chatKey, true);
                        atualizarListaContatosComNotificacao();
                    }
                }
            }
            case STATUS_UPDATE -> {
                String[] parts = msg.getContent().split(":", 4);
                if (parts.length >= 2) {
                    String messageIdToUpdate = parts[0];
                    try {
                        MessageStatus newStatus = MessageStatus.valueOf(parts[1]);
                        Date relevantTime = (parts.length > 3) ? new Date(Long.parseLong(parts[3])) : new Date();
                        updateMessageStatusOnGUI(messageIdToUpdate, newStatus, relevantTime);
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Status de mensagem inválido recebido: " + parts[1]);
                    }
                }
            }
            case GROUP_CREATE -> {
                String newGroupName = msg.getContent();
                System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] DEBUG: Recebeu GROUP_CREATE para: " + newGroupName + ". Sender: " + msg.getSender() + ", Receiver: " + msg.getReceiver());

                if (!gruposParticipando.contains(newGroupName)) {
                    gruposParticipando.add(newGroupName);
                }
                boolean foundInModel = false;
                for(int i=0; i < userModel.getSize(); i++){
                    if(userModel.getElementAt(i).replace(NOTIFICATION_ICON,"").trim().equals(newGroupName)){
                        foundInModel = true;
                        break;
                    }
                }
                if(!foundInModel){
                    userModel.addElement(newGroupName);
                }
                historicoMensagens.putIfAbsent(newGroupName, new ArrayList<>());
                atualizarListaContatosComNotificacao();
            }
            case GROUP_SYSTEM_MESSAGE -> {
                String targetGroupForSystemMsg = msg.getReceiver();
                System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] DEBUG: Recebeu GROUP_SYSTEM_MESSAGE para grupo '" + targetGroupForSystemMsg + "': " + msg.getContent());
                historicoMensagens.computeIfAbsent(targetGroupForSystemMsg, k -> new ArrayList<>()).add(msg);

                if (isInChatView && currentChat != null && currentChat.equals(targetGroupForSystemMsg)) {
                    addSystemMessageToPanel(msg.getContent());
                } else {
                    notificacoes.put(targetGroupForSystemMsg, true);
                    atualizarListaContatosComNotificacao();
                }
            }
            case GROUP_REMOVED_NOTIFICATION -> {
                String groupToRemove = msg.getContent();
                System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] DEBUG: Recebeu GROUP_REMOVED_NOTIFICATION para: " + groupToRemove);
                gruposParticipando.remove(groupToRemove);

                for (int i = 0; i < userModel.getSize(); i++) {
                    if (userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim().equals(groupToRemove)) {
                        userModel.removeElementAt(i);
                        break;
                    }
                }
                historicoMensagens.remove(groupToRemove);
                notificacoes.remove(groupToRemove);

                if (isInChatView && currentChat != null && currentChat.equals(groupToRemove)) {
                    showContactsView();
                     JOptionPane.showMessageDialog(ClientGUI.this, "Você não faz mais parte do grupo: " + groupToRemove.replace(GROUP_ICON_PREFIX,""), "Grupo Deixado", JOptionPane.INFORMATION_MESSAGE);
                }
                atualizarListaContatosComNotificacao();
            }
            case GROUP_INFO_RESPONSE -> {
                String groupNameOfInfo = msg.getSender(); // O servidor envia o nome do grupo como 'sender' aqui
                String membersListString = msg.getContent();
                System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] DEBUG: Recebeu GROUP_INFO_RESPONSE para '" + groupNameOfInfo + "': " + membersListString);
                if (isInChatView && currentChat != null && currentChat.equals(groupNameOfInfo)) {
                    showGroupInfoDialog(groupNameOfInfo, membersListString);
                }
            }
            case TEXT -> { // Mensagens de TEXTO genéricas, geralmente do Servidor para informação/erro
                if ("Servidor".equals(msg.getSender())) {
                    JOptionPane.showMessageDialog(ClientGUI.this, msg.getContent(), "Mensagem do Servidor", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            // Se MESSAGE_READ precisar de alguma ação visual específica no futuro, adicionar um case aqui.
            default -> System.out.println("[" + (currentGuiUsername != null ? currentGuiUsername : "NO_USERNAME") + "] WARN: Tipo de mensagem não tratado recebido na GUI: " + type);
        }
    }

    private void atualizarListaContatosComNotificacao() {
        if (userList == null) return;
        String selectedValue = userList.getSelectedValue();

        List<String> currentRawItemsInModel = new ArrayList<>();
        for (int i = 0; i < userModel.getSize(); i++) {
            currentRawItemsInModel.add(userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim());
        }
        Set<String> uniqueItems = new LinkedHashSet<>(currentRawItemsInModel);
        List<String> sortedList = new ArrayList<>(uniqueItems);

        sortedList.sort((s1, s2) -> {
            boolean s1IsGroup = s1.startsWith(GROUP_ICON_PREFIX);
            boolean s2IsGroup = s2.startsWith(GROUP_ICON_PREFIX);

            if (!s1IsGroup && s2IsGroup) return -1;
            if (s1IsGroup && !s2IsGroup) return 1;
            return s1.compareToIgnoreCase(s2);
        });

        DefaultListModel<String> newPopulatedModel = new DefaultListModel<>();
        for (String itemName : sortedList) {
            String nameWithoutIcon = itemName;
            if (notificacoes.getOrDefault(nameWithoutIcon, false) && (currentChat == null || !nameWithoutIcon.equals(currentChat.replace(NOTIFICATION_ICON, "").trim()))) {
                newPopulatedModel.addElement(nameWithoutIcon + NOTIFICATION_ICON);
            } else {
                newPopulatedModel.addElement(nameWithoutIcon);
            }
        }

        userModel.clear();
        for(int i = 0; i < newPopulatedModel.getSize(); i++) {
            userModel.addElement(newPopulatedModel.getElementAt(i));
        }

        if (selectedValue != null) {
            boolean found = false;
            String cleanSelectedValue = selectedValue.replace(NOTIFICATION_ICON, "").trim();
            for (int i = 0; i < userModel.getSize(); i++) {
                if (userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim().equals(cleanSelectedValue)) {
                    userList.setSelectedValue(userModel.getElementAt(i), true);
                    found = true;
                    break;
                }
            }
            if (!found) userList.clearSelection();
        } else {
            userList.clearSelection();
        }
        userList.repaint();
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    private static class RoundBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        private final boolean filledBackground;
        private final int thickness;

        public RoundBorder(int radius, Color color, boolean filledBackground) {
            this.radius = radius;
            this.color = color;
            this.filledBackground = filledBackground;
            this.thickness = 1;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (filledBackground && c.isOpaque()) {
                g2.setColor(c.getBackground());
                g2.fillRoundRect(x, y, width - thickness, height - thickness, radius, radius);
            }

            g2.setColor(this.color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - thickness, height - thickness, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int p = filledBackground ? radius / 2 + 2 : thickness + 2;
            return new Insets(p, p + 3, p, p + 3);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            int p = filledBackground ? radius / 2 + 2 : thickness + 2;
            insets.left = insets.right = p + 3;
            insets.top = insets.bottom = p;
            return insets;
        }

         @Override
        public boolean isBorderOpaque() {
            return !filledBackground;
        }
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    UIManager.put("control", new Color(245, 245, 245));
                    UIManager.put("nimbusBase", new Color(7, 94, 84));
                    UIManager.put("nimbusFocus", new Color(37, 211, 102));
                    UIManager.put("text", Color.BLACK);
                    UIManager.put("List.background", Color.WHITE);
                    UIManager.put("List.selectionBackground", new Color(37, 211, 102));
                    UIManager.put("List.selectionForeground", Color.WHITE);
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                System.err.println("Falha ao configurar Look and Feel: " + ex.getMessage());
            }
        }
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}