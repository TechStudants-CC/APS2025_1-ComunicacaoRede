package client;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.List;
import common.Message;
import common.MessageType;
import common.MessageStatus;

public class ClientGUI extends JFrame {
    private DefaultListModel<String> userModel;
    private JList<String> userList;
    private Client client;
    private String username;
    private List<String> grupos = new ArrayList<>();
    private Map<String, List<Message>> historicoMensagens = new HashMap<>();
    private Map<String, Boolean> notificacoes = new HashMap<>();
    private Map<String, JPanel> messagePanels = new HashMap<>();

    // Cores
    private final Color primaryColor = new Color(7, 94, 84);
    private final Color secondaryColor = new Color(37, 211, 102);
    private final Color accentColor = new Color(18, 140, 126);
    private final Color background = new Color(236, 229, 221);
    private final Color chatPanelBackground = new Color(224, 237, 232);
    private final Color listBackground = Color.WHITE;
    private final Color textColor = Color.BLACK;
    private final Color mutedTextColor = new Color(100, 100, 100);

    private final Color sentMessageColor = new Color(220, 248, 198);
    private final Color receivedMessageColor = Color.WHITE;
    private final Color readStatusColor = new Color(52, 183, 241);

    // Componentes
    private JPanel mainPanel;
    private JPanel contactsPanel;
    private JPanel chatPanel;
    private JPanel chatMessagesPanel;
    private JScrollPane chatScrollPane;
    private JTextField inputField;
    private String currentChat;

    private boolean isInChatView = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    private static final String NOTIFICATION_ICON = " \uD83D\uDD34";

    private class ContactListRenderer extends DefaultListCellRenderer {
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
                label.setForeground(textColor);
            }

            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(listBackground);
            }
            return label;
        }
    }

    public ClientGUI() {
        showCustomLoginDialog();
        
        if (username != null && !username.isEmpty() && client != null) {
            setupInterface();
        } else {
            System.err.println("Login não concluído. Aplicação será encerrada.");
            System.exit(0); 
        }
    }

    private void showCustomLoginDialog() {
        JDialog loginDialog = new JDialog(this, "Entrar no Chat", true);
        
        JPanel dialogMainPanel = new JPanel(new GridBagLayout());
        dialogMainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        dialogMainPanel.setBackground(new Color(248, 248, 248));
        loginDialog.setContentPane(dialogMainPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Chat Ambiental");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(primaryColor);
        gbc.gridwidth = 2;
        dialogMainPanel.add(titleLabel, gbc);

        gbc.gridy++; 
        gbc.insets = new Insets(20, 5, 5, 5); 

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        JLabel nameLabel = new JLabel("Seu nome:");
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        JTextField nameField = new JTextField(20);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        ((AbstractDocument) nameField.getDocument()).setDocumentFilter(new DocumentFilter() {
            // O método capitalize que estava aqui foi removido pois não era chamado.
            // A lógica de capitalização está diretamente em replace e insertString.
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String currentTextBeforeChange = fb.getDocument().getText(0, fb.getDocument().getLength());
                String newTextSegment = text;

                if (newTextSegment != null && newTextSegment.length() > 0) {
                    // Capitaliza se for o início do campo ou se o caractere anterior for um espaço.
                    if (offset == 0 || (offset > 0 && Character.isWhitespace(currentTextBeforeChange.charAt(offset - 1)))) {
                         newTextSegment = newTextSegment.substring(0, 1).toUpperCase() + newTextSegment.substring(1);
                    }
                }
                super.replace(fb, offset, length, newTextSegment, attrs);
            }

             @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                 String currentTextBeforeChange = fb.getDocument().getText(0, fb.getDocument().getLength());
                 String newStringSegment = string;
                 if (newStringSegment != null && newStringSegment.length() > 0) {
                     if (offset == 0 || (offset > 0 && Character.isWhitespace(currentTextBeforeChange.charAt(offset - 1)))) {
                         newStringSegment = newStringSegment.substring(0, 1).toUpperCase() + newStringSegment.substring(1);
                     }
                 }
                super.insertString(fb, offset, newStringSegment, attr);
            }
        });
        dialogMainPanel.add(nameField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        JLabel ipLabel = new JLabel("IP do Servidor:");
        ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dialogMainPanel.add(ipLabel, gbc);

        gbc.gridx = 1;
        JTextField serverIpField = new JTextField(20);
        serverIpField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        serverIpField.setText("127.0.0.1");
        dialogMainPanel.add(serverIpField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 5, 10, 5);
        JButton enterButton = new JButton("Entrar");
        enterButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        enterButton.setBackground(secondaryColor);
        enterButton.setForeground(Color.WHITE);
        enterButton.setFocusPainted(false);
        enterButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enterButton.setPreferredSize(new Dimension(140, 45));
        dialogMainPanel.add(enterButton, gbc);

        final String[] tempUsername = new String[1];

        ActionListener loginAction = e -> {
            tempUsername[0] = nameField.getText().trim();
            String serverIp = serverIpField.getText().trim();
            if (!tempUsername[0].isEmpty() && !serverIp.isEmpty()) {
                try {
                    this.client = new Client(serverIp, 54321, tempUsername[0], ClientGUI.this);
                    this.username = tempUsername[0];
                    loginDialog.dispose();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(loginDialog, "Falha na conexão: " + ex.getMessage(), "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Nome e IP são obrigatórios!", "Entrada Inválida", JOptionPane.WARNING_MESSAGE);
            }
        };

        nameField.addActionListener(loginAction);
        serverIpField.addActionListener(loginAction);
        enterButton.addActionListener(loginAction);

        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                 if (username == null || username.isEmpty()) {
                    System.exit(0);
                }
            }
        });

        loginDialog.pack();
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);
    }

    private void setupInterface() {
        setTitle("ChatApp - " + username);
        setSize(370, 680);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(background);

        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(background);

        createContactsPanel();
        createChatPanel();

        mainPanel.add(contactsPanel, "contacts");
        mainPanel.add(chatPanel, "chat");

        add(mainPanel, BorderLayout.CENTER);
        setVisible(true);
    }

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
        
        JButton btnNewGroup = new JButton("\uD83D\uDC65+");
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

        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setCellRenderer(new ContactListRenderer());
        userList.setBackground(listBackground);
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedValue = userList.getSelectedValue();
                    if (selectedValue != null) {
                        String contactName = selectedValue.replace(NOTIFICATION_ICON, "").trim();
                        if (!contactName.equals(username)) {
                            notificacoes.put(contactName, false);
                            showChatView(contactName);
                            atualizarListaContatosComNotificacao();
                        }
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220,220,220)));
        contactsPanel.add(scrollPane, BorderLayout.CENTER);
    }

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

        JLabel chatTitleLabel = new JLabel("", SwingConstants.LEFT);
        chatTitleLabel.setName("chatTitleLabel");
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        chatTitleLabel.setForeground(Color.WHITE);
        chatTitleLabel.setBorder(new EmptyBorder(0, 15, 0, 0));
        headerChatPanel.add(chatTitleLabel, BorderLayout.CENTER);
        chatPanel.add(headerChatPanel, BorderLayout.NORTH);

        chatMessagesPanel = new JPanel();
        chatMessagesPanel.setLayout(new BoxLayout(chatMessagesPanel, BoxLayout.Y_AXIS));
        chatMessagesPanel.setBackground(chatPanelBackground);
        chatMessagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

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

    private void addMessageToPanel(Message msg, boolean isOwnMessage) {
        JPanel messageRowPanel = new JPanel(new FlowLayout(isOwnMessage ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 3));
        messageRowPanel.setBackground(chatPanelBackground);

        JPanel bubbleWrapper = new JPanel();
        bubbleWrapper.setLayout(new BoxLayout(bubbleWrapper, BoxLayout.X_AXIS));
        bubbleWrapper.setBackground(chatPanelBackground);
        
        JPanel bubblePanel = new JPanel(new BorderLayout(5, 3));
        bubblePanel.setBorder(new CompoundBorder(
                new RoundBorder(15, isOwnMessage ? sentMessageColor : receivedMessageColor, false),
                new EmptyBorder(8, 12, 5, 12)
        ));
        bubblePanel.setBackground(isOwnMessage ? sentMessageColor : receivedMessageColor);
        
        JTextArea messageText = new JTextArea(msg.getContent());
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
    
    private void updateMessageStatusOnGUI(String messageId, MessageStatus newStatus, Date relevantTime) {
        JPanel bubblePanel = messagePanels.get(messageId);
        if (bubblePanel != null) {
            Component footerComponent = bubblePanel.getComponent(1); 
            if (footerComponent instanceof JPanel) {
                JPanel footerPanel = (JPanel) footerComponent;
                for (Component comp : footerPanel.getComponents()) {
                    if (comp instanceof JLabel && comp.getName() != null && comp.getName().equals("statusLabel_" + messageId)) {
                        JLabel statusLabel = (JLabel) comp;
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
        if (status == null) return " ";
        switch (status) {
            case SENDING: return "⏳"; 
            case SENT: return "✓";    
            case DELIVERED: return "✓✓"; 
            case READ: return "✓✓"; 
            case FAILED: return "✗"; 
            default: return " ";
        }
    }
    
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            if (vertical != null) {
                 vertical.setValue(vertical.getMaximum());
            }
            chatMessagesPanel.revalidate();
            chatMessagesPanel.repaint();
        });
    }

    private void showContactsView() {
        isInChatView = false;
        currentChat = null;
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
    }

    private void showChatView(String contactOrGroupName) {
        currentChat = contactOrGroupName;
        isInChatView = true;

        JLabel chatTitle = null;
        JPanel headerChatPanel = (JPanel) chatPanel.getComponent(0);
        for(Component comp : headerChatPanel.getComponents()){
            if(comp.getName() != null && comp.getName().equals("chatTitleLabel")){
                chatTitle = (JLabel)comp;
                break;
            }
        }
        if(chatTitle != null) {
            chatTitle.setText(contactOrGroupName.replace(NOTIFICATION_ICON, "").trim());
        }

        chatMessagesPanel.removeAll(); 
        messagePanels.clear(); 

        List<Message> historico = historicoMensagens.getOrDefault(currentChat, new ArrayList<>());
        for (Message msg : historico) {
            boolean isOwn = msg.getSender().equals(username);
            addMessageToPanel(msg, isOwn);
            if (!isOwn && msg.getStatus() != MessageStatus.READ) {
                client.sendMessage(new Message(msg.getMessageId(), username, msg.getSender(), "READ_CONFIRMATION", MessageType.MESSAGE_READ));
            }
        }
        
        notificacoes.put(currentChat, false);
        atualizarListaContatosComNotificacao();
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
        scrollToBottom(); 
        inputField.requestFocusInWindow();
    }
    
    private void createGroup() {
        List<String> selectedUsersOnList = userList.getSelectedValuesList();
        if (selectedUsersOnList == null || selectedUsersOnList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione pelo menos um usuário (além de você) para criar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        List<String> membersToInvite = new ArrayList<>();
        for (String selected : selectedUsersOnList) {
            String cleanName = selected.replace(NOTIFICATION_ICON, "").trim();
            if (!cleanName.equals(username)) { 
                membersToInvite.add(cleanName);
            }
        }

        if (membersToInvite.isEmpty()){ 
            JOptionPane.showMessageDialog(this, "Selecione outros usuários (diferentes de você) para formar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String groupNameInput = JOptionPane.showInputDialog(this, "Digite o nome do grupo:", "Novo Grupo", JOptionPane.PLAIN_MESSAGE);
        if (groupNameInput == null || groupNameInput.trim().isEmpty()) {
            return; 
        }
        String fullGroupName = "\uD83D\uDC65 " + groupNameInput.trim();

        if (grupos.contains(fullGroupName) || userModel.contains(fullGroupName)) {
            JOptionPane.showMessageDialog(this, "Um grupo com este nome já existe.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> allMembersForServer = new ArrayList<>(membersToInvite);
        allMembersForServer.add(username); 

        String groupCreationContent = fullGroupName + ";" + String.join(",", allMembersForServer);
        Message groupCreateMsg = new Message(username, "Servidor", groupCreationContent, MessageType.GROUP_CREATE);
        client.sendMessage(groupCreateMsg);
        
        if (!grupos.contains(fullGroupName)) grupos.add(fullGroupName);
        if (!userModel.contains(fullGroupName)) userModel.addElement(fullGroupName);
        historicoMensagens.putIfAbsent(fullGroupName, new ArrayList<>());
        atualizarListaContatosComNotificacao();
    }


    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            MessageType type = currentChat.startsWith("\uD83D\uDC65") ? MessageType.GROUP : MessageType.PRIVATE;
            String receiver = currentChat; 
            
            Message msg = new Message(username, receiver, text, type);
            msg.setStatus(MessageStatus.SENDING);

            client.sendMessage(msg); 

            historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(msg);
            addMessageToPanel(msg, true); 
            inputField.setText("");
            scrollToBottom();
        }
    }

    private void sendFile() {
        if (currentChat == null) {
            showError("Selecione uma conversa para enviar o arquivo.");
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
                
                MessageType type = currentChat.startsWith("\uD83D\uDC65") ? MessageType.GROUP : MessageType.PRIVATE;
                String receiver = currentChat;
                String fileMessageContent = "Arquivo: " + fileName;

                Message fileMessage = new Message(username, receiver, fileMessageContent, type);
                fileMessage.setFileData(fileData);
                fileMessage.setFileName(fileName);
                fileMessage.setStatus(MessageStatus.SENDING);

                client.sendMessage(fileMessage);

                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(fileMessage);
                addMessageToPanel(fileMessage, true);
                scrollToBottom();

            } catch (IOException e) {
                showError("Erro ao ler o arquivo: " + e.getMessage());
            }
        }
    }
    
    public void handleMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            String chatKey; 

            switch (msg.getType()) {
                case USER_LIST:
                    List<String> onlineUsernames = new ArrayList<>(Arrays.asList(msg.getContent().split(",")));
                    userModel.clear(); 
                    onlineUsernames.stream()
                        .filter(user -> !user.trim().isEmpty() && !user.equals(username))
                        .forEach(userModel::addElement);
                    grupos.forEach(groupName -> {
                        if (!userModel.contains(groupName)) { 
                            userModel.addElement(groupName);
                        }
                    });
                    atualizarListaContatosComNotificacao();
                    break;

                case PRIVATE:
                case GROUP: 
                    boolean isOwnMessage = msg.getSender().equals(username);
                    
                    if (msg.getType() == MessageType.GROUP) {
                        chatKey = msg.getReceiver(); 
                    } else { 
                        chatKey = isOwnMessage ? msg.getReceiver() : msg.getSender();
                    }

                    if (msg.getFileData() != null && msg.getFileName() != null && !isOwnMessage) {
                        try {
                            File downloadsDir = new File(System.getProperty("user.home") + File.separator + "ChatApp_Downloads" + File.separator + username);
                            if (!downloadsDir.exists()) {
                                downloadsDir.mkdirs();
                            }
                            File receivedFile = new File(downloadsDir, msg.getFileName());
                            Files.write(receivedFile.toPath(), msg.getFileData());
                            
                            String newContent = String.format("Arquivo '%s' recebido de %s e salvo em: %s", 
                                                              msg.getFileName(), msg.getSender(), receivedFile.getAbsolutePath());
                            msg.setContent(newContent); 
                            
                            JOptionPane.showMessageDialog(this, 
                                "Arquivo '" + msg.getFileName() + "' recebido de " + msg.getSender() + 
                                "\nsalvo em: " + receivedFile.getAbsolutePath(), 
                                "Arquivo Recebido", JOptionPane.INFORMATION_MESSAGE);
                        } catch (IOException e) {
                            msg.setContent("Erro ao salvar arquivo '" + msg.getFileName() + "' recebido de " + msg.getSender());
                            showError("Erro ao salvar arquivo recebido: " + e.getMessage());
                        }
                    }
                    
                    if (!isOwnMessage) { 
                        historicoMensagens.computeIfAbsent(chatKey, k -> new ArrayList<>()).add(msg);
                        if (isInChatView && currentChat.equals(chatKey)) {
                            addMessageToPanel(msg, false); 
                            client.sendMessage(new Message(msg.getMessageId(), username, msg.getSender(), "READ_CONFIRMATION", MessageType.MESSAGE_READ));
                        } else {
                            notificacoes.put(chatKey, true);
                            atualizarListaContatosComNotificacao();
                        }
                    }
                    break;
                    
                case STATUS_UPDATE:
                    String[] parts = msg.getContent().split(":", 4); 
                    String messageIdToUpdate = parts[0];
                    MessageStatus newStatus = MessageStatus.valueOf(parts[1]);
                    Date relevantTime = (parts.length > 3) ? new Date(Long.parseLong(parts[3])) : new Date();
                    
                    updateMessageStatusOnGUI(messageIdToUpdate, newStatus, relevantTime);
                    break;

                case GROUP_CREATE: 
                    String newGroupName = msg.getContent(); 
                    if (!grupos.contains(newGroupName)) {
                        grupos.add(newGroupName);
                        if (!userModel.contains(newGroupName)) { 
                            userModel.addElement(newGroupName);
                        }
                        historicoMensagens.putIfAbsent(newGroupName, new ArrayList<>());
                        if (msg.getSender().equals("Servidor") && msg.getReceiver().equals(username)) {
                             JOptionPane.showMessageDialog(this, "Você foi adicionado ao grupo: " + newGroupName, "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                    atualizarListaContatosComNotificacao();
                    break;
                
                case TEXT: 
                    if (msg.getSender().equals("Servidor")) { 
                        JOptionPane.showMessageDialog(this, msg.getContent(), "Mensagem do Servidor", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;

                default:
                    System.out.println("Tipo de mensagem não tratado recebido na GUI: " + msg.getType());
            }
        });
    }
    
    private void atualizarListaContatosComNotificacao() {
        List<String> currentElements = new ArrayList<>();
        for (int i = 0; i < userModel.getSize(); i++) {
            currentElements.add(userModel.getElementAt(i));
        }
        for (String group : grupos) {
            boolean found = false;
            for (String el : currentElements) {
                if (el.replace(NOTIFICATION_ICON, "").trim().equals(group)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                currentElements.add(group);
            }
        }

        userModel.clear();
        for (String el : currentElements) {
            String nameWithoutIcon = el.replace(NOTIFICATION_ICON, "").trim();
            if (notificacoes.getOrDefault(nameWithoutIcon, false)) {
                userModel.addElement(nameWithoutIcon + NOTIFICATION_ICON);
            } else {
                userModel.addElement(nameWithoutIcon);
            }
        }
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }
    
    private static class RoundBorder extends AbstractBorder {
        private int radius;
        private Color color;
        private boolean filledBackground; 
        private int thickness = 1; 

        public RoundBorder(int radius, Color color, boolean filledBackground) {
            this.radius = radius;
            this.color = color;
            this.filledBackground = filledBackground;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (filledBackground && c.isOpaque()) { 
                g2.setColor(this.color); 
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
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Falha ao configurar Look and Feel: " + ex.getMessage());
            }
        }
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}