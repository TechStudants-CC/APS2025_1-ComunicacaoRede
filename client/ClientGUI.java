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
    private List<String> gruposParticipando = new ArrayList<>(); // Renomeado para clareza
    private Map<String, List<Message>> historicoMensagens = new HashMap<>();
    private Map<String, Boolean> notificacoes = new HashMap<>();
    private Map<String, JPanel> messagePanels = new HashMap<>();

    // Cores e Constantes
    private final Color primaryColor = new Color(7, 94, 84);
    private final Color secondaryColor = new Color(37, 211, 102);
    private final Color accentColor = new Color(18, 140, 126);
    private final Color background = new Color(236, 229, 221);
    private final Color chatPanelBackground = new Color(224, 237, 232); // Fundo da área de chat (efeito papel de parede)
    private final Color listBackground = Color.WHITE;
    private final Color textColor = Color.BLACK;
    private final Color mutedTextColor = new Color(100, 100, 100);
    private final Color sentMessageColor = new Color(220, 248, 198);
    private final Color receivedMessageColor = Color.WHITE;
    private final Color readStatusColor = new Color(52, 183, 241);
    private final int MESSAGE_SPACING = 6; // Espaçamento vertical entre mensagens

    // Componentes da UI
    private JPanel mainPanel;
    private JPanel contactsPanel;
    private JPanel chatPanel;
    private JPanel chatMessagesPanel; // Onde as bolhas de mensagem são adicionadas
    private JScrollPane chatScrollPane;
    private JTextField inputField;
    private JButton btnLeaveGroup; // Botão para sair do grupo
    private String currentChat; // Nome do contato ou grupo atual

    private boolean isInChatView = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    private static final String NOTIFICATION_ICON = " \uD83D\uDD34"; // Círculo vermelho para notificação
    private static final String GROUP_ICON_PREFIX = "\uD83D\uDC65 "; // Ícone para grupos

    // Renderizador para a lista de contatos e grupos
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
                label.setBackground(list.getSelectionBackground()); // Usa cores de seleção do L&F
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(listBackground);
            }
            return label;
        }
    }

    // Construtor principal
    public ClientGUI() {
        showCustomLoginDialog(); // Mostra o diálogo de login
        
        if (username != null && !username.isEmpty() && client != null) {
            setupInterface(); // Configura a interface principal se o login for bem-sucedido
        } else {
            System.err.println("Login não concluído. Aplicação será encerrada.");
            System.exit(0); 
        }
    }

    // Diálogo de Login
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
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String currentTextBeforeChange = fb.getDocument().getText(0, fb.getDocument().getLength());
                String newTextSegment = text;

                if (newTextSegment != null && newTextSegment.length() > 0) {
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

    // Configuração da Interface Principal
    private void setupInterface() {
        setTitle("ChatApp - " + username);
        setSize(375, 700); // Ajuste leve no tamanho para acomodar melhor os elementos
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

    // Painel de Contatos
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
        
        JButton btnNewGroup = new JButton(GROUP_ICON_PREFIX + "+"); // Usando o prefixo de ícone
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
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); // Permite selecionar múltiplos para criar grupo
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedValue = userList.getSelectedValue();
                    if (selectedValue != null) {
                        String contactName = selectedValue.replace(NOTIFICATION_ICON, "").trim();
                        if (!contactName.equals(username)) { // Não pode abrir chat consigo mesmo
                            notificacoes.put(contactName, false);
                            showChatView(contactName);
                            atualizarListaContatosComNotificacao();
                        }
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1,0,0,0, new Color(220,220,220))); // Linha sutil no topo
        contactsPanel.add(scrollPane, BorderLayout.CENTER);
    }

    // Painel de Chat
    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout(0,0));
        chatPanel.setBackground(background);

        // Cabeçalho do Chat (Nome, Botão Voltar, Botão Sair do Grupo)
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

        btnLeaveGroup = new JButton("Sair do Grupo");
        btnLeaveGroup.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLeaveGroup.setForeground(Color.WHITE);
        btnLeaveGroup.setBackground(accentColor.darker()); // Um pouco mais escuro para diferenciar
        btnLeaveGroup.setToolTipText("Deixar este grupo");
        btnLeaveGroup.setFocusPainted(false);
        btnLeaveGroup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLeaveGroup.setVisible(false); // Inicialmente invisível
        btnLeaveGroup.addActionListener(e -> leaveGroup());
        headerChatPanel.add(btnLeaveGroup, BorderLayout.EAST);

        chatPanel.add(headerChatPanel, BorderLayout.NORTH);

        // Área de Mensagens
        chatMessagesPanel = new JPanel();
        chatMessagesPanel.setLayout(new BoxLayout(chatMessagesPanel, BoxLayout.Y_AXIS));
        chatMessagesPanel.setBackground(chatPanelBackground);
        chatMessagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        chatScrollPane = new JScrollPane(chatMessagesPanel);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Painel de Entrada de Texto
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(background); // Usa a cor de fundo geral
        inputPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
        
        JButton btnAttach = createIconButton("\uD83D\uDCCE"); // Ícone de anexo
        btnAttach.setToolTipText("Anexar arquivo");
        btnAttach.addActionListener(e -> sendFile());
        
        JPanel attachButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0,0));
        attachButtonPanel.setOpaque(false);
        attachButtonPanel.add(btnAttach);
        attachButtonPanel.setBorder(new EmptyBorder(0,0,0,5)); // Pequeno espaço à direita do botão de anexo


        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        inputField.setBorder(new RoundBorder(25, Color.WHITE, true)); // Borda arredondada preenchida
        inputField.setBackground(Color.WHITE);
        inputField.addActionListener(e -> sendMessage());
        
        JPanel textAndAttachPanel = new JPanel(new BorderLayout()); // Painel para agrupar anexo e campo de texto
        textAndAttachPanel.setOpaque(false); // Transparente
        textAndAttachPanel.add(attachButtonPanel, BorderLayout.WEST);
        textAndAttachPanel.add(inputField, BorderLayout.CENTER);

        inputPanel.add(textAndAttachPanel, BorderLayout.CENTER);


        JButton btnSend = new JButton("\u27A4"); // Ícone de enviar (seta)
        btnSend.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
        btnSend.setBackground(secondaryColor);
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setBorder(new RoundBorder(25, secondaryColor, false)); // Borda arredondada para o botão
        btnSend.setPreferredSize(new Dimension(50, 50)); // Faz o botão mais redondo/quadrado
        btnSend.addActionListener(e -> sendMessage());
        inputPanel.add(btnSend, BorderLayout.EAST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    }
    
    // Criação de botões de ícone (para anexo, etc.)
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

    // Adiciona uma mensagem à interface de chat
    private void addMessageToPanel(Message msg, boolean isOwnMessage) {
        JPanel messageRowPanel = new JPanel(new FlowLayout(isOwnMessage ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0)); // Vgap 0 aqui
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
        
        // Se for mensagem de grupo e não for minha, prefixa com o nome do remetente
        String messageContent = msg.getContent();
        if (msg.getType() == MessageType.GROUP && !isOwnMessage) {
            messageContent = msg.getSender() + ":\n" + msg.getContent();
        }

        JTextArea messageText = new JTextArea(messageContent);
        messageText.setEditable(false);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        messageText.setOpaque(false); // Para que a cor do balão apareça
        bubblePanel.add(messageText, BorderLayout.CENTER);

        // Painel para rodapé (hora, status, botão de download)
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
        } else if (msg.getFileData() != null && msg.getFileName() != null) { // Se for mensagem recebida com arquivo
            JButton btnDownloadFile = new JButton("\uD83D\uDCE5"); // Ícone de download
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
            messageRowPanel.add(Box.createHorizontalGlue()); // Empurra para direita
            messageRowPanel.add(bubbleWrapper);
        } else {
            messageRowPanel.add(bubbleWrapper);
            messageRowPanel.add(Box.createHorizontalGlue()); // Empurra para esquerda
        }

        int chatAreaWidth = chatScrollPane.getViewport().getWidth() > 0 ? chatScrollPane.getViewport().getWidth() : chatMessagesPanel.getWidth();
        if(chatAreaWidth <= 0) chatAreaWidth = (int)(this.getWidth() * 0.8);
        bubbleWrapper.setMaximumSize(new Dimension((int)(chatAreaWidth * 0.75), Integer.MAX_VALUE)); // Limita largura do balão
        
        chatMessagesPanel.add(messageRowPanel);
        chatMessagesPanel.add(Box.createVerticalStrut(MESSAGE_SPACING)); // ESPAÇAMENTO CONSISTENTE AQUI
        messagePanels.put(msg.getMessageId(), bubblePanel);

        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
        scrollToBottom();
    }

    // Método para baixar/salvar arquivo
    private void downloadFile(String fileName, byte[] fileData) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Salvar arquivo como...");
        fileChooser.setSelectedFile(new File(fileName)); // Sugere o nome original do arquivo

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
    
    // Atualiza o status de uma mensagem na GUI
    private void updateMessageStatusOnGUI(String messageId, MessageStatus newStatus, Date relevantTime) {
        JPanel bubblePanel = messagePanels.get(messageId);
        if (bubblePanel != null) {
            // Encontra o JLabel do status dentro do painel de rodapé do balão
            Component footerComponent = bubblePanel.getComponent(1); // BorderLayout.SOUTH
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
            // Atualiza o status no histórico local também
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

    // Retorna o ícone Unicode para o status da mensagem
    private String getStatusIcon(MessageStatus status) {
        if (status == null) return " "; 
        switch (status) {
            case SENDING: return "⏳"; // Relógio
            case SENT: return "✓";    // Um tique
            case DELIVERED: return "✓✓"; // Dois tiques
            case READ: return "✓✓"; // Dois tiques (a cor azul é aplicada separadamente)
            case FAILED: return "✗"; // X
            default: return " "; // Espaço para manter alinhamento se status for desconhecido
        }
    }
    
    // Rola o painel de chat para a última mensagem
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

    // Mostra a visualização de contatos
    private void showContactsView() {
        isInChatView = false;
        currentChat = null;
        if(btnLeaveGroup != null) btnLeaveGroup.setVisible(false); // Esconde o botão de sair do grupo
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
    }

    // Mostra a visualização de chat para um contato ou grupo
    private void showChatView(String contactOrGroupName) {
        currentChat = contactOrGroupName;
        isInChatView = true;

        // Atualiza o título do chat
        JLabel chatTitle = null;
        JPanel headerChatPanel = (JPanel) chatPanel.getComponent(0); // BorderLayout.NORTH
        for(Component comp : headerChatPanel.getComponents()){
            if(comp.getName() != null && comp.getName().equals("chatTitleLabel")){
                chatTitle = (JLabel)comp;
                break;
            }
        }
        if(chatTitle != null) {
            chatTitle.setText(contactOrGroupName.replace(NOTIFICATION_ICON, "").trim());
        }

        // Mostra ou esconde o botão "Sair do Grupo"
        if (btnLeaveGroup != null) {
            btnLeaveGroup.setVisible(contactOrGroupName.startsWith(GROUP_ICON_PREFIX));
        }

        // Limpa e recarrega mensagens
        chatMessagesPanel.removeAll(); 
        messagePanels.clear(); 

        List<Message> historico = historicoMensagens.getOrDefault(currentChat, new ArrayList<>());
        for (Message msg : historico) {
            boolean isOwn = msg.getSender().equals(username);
            addMessageToPanel(msg, isOwn);
            // Se a mensagem não for minha, não foi lida e o chat está sendo aberto, envia confirmação de leitura
            if (!isOwn && msg.getStatus() != MessageStatus.READ) {
                client.sendMessage(new Message(msg.getMessageId(), username, msg.getSender(), "READ_CONFIRMATION", MessageType.MESSAGE_READ));
            }
        }
        
        notificacoes.put(currentChat, false); // Remove notificação ao abrir o chat
        atualizarListaContatosComNotificacao(); // Atualiza a lista (remove o ícone de notificação)
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
        scrollToBottom(); 
        inputField.requestFocusInWindow();
    }
    
    // Cria um novo grupo
    private void createGroup() {
        List<String> selectedUsersOnList = userList.getSelectedValuesList();
        if (selectedUsersOnList == null || selectedUsersOnList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione pelo menos um usuário (além de você) para criar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        List<String> membersToInvite = new ArrayList<>();
        for (String selected : selectedUsersOnList) {
            String cleanName = selected.replace(NOTIFICATION_ICON, "").trim().replace(GROUP_ICON_PREFIX,"").trim();
            if (!cleanName.equals(username) && !cleanName.startsWith(GROUP_ICON_PREFIX)) { // Não convida a si mesmo nem outros grupos
                membersToInvite.add(cleanName);
            }
        }

        if (membersToInvite.isEmpty()){ 
            JOptionPane.showMessageDialog(this, "Selecione outros usuários válidos (diferentes de você e que não sejam grupos) para formar um grupo.", "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String groupNameInput = JOptionPane.showInputDialog(this, "Digite o nome do grupo:", "Novo Grupo", JOptionPane.PLAIN_MESSAGE);
        if (groupNameInput == null || groupNameInput.trim().isEmpty()) {
            return; // Usuário cancelou ou não digitou nome
        }
        String fullGroupName = GROUP_ICON_PREFIX + groupNameInput.trim(); // Adiciona ícone

        // Verifica se o nome do grupo já existe (localmente)
        if (gruposParticipando.contains(fullGroupName) || userModel.contains(fullGroupName)) {
            JOptionPane.showMessageDialog(this, "Um grupo com este nome já existe na sua lista.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> allMembersForServer = new ArrayList<>(membersToInvite);
        allMembersForServer.add(username); // Adiciona o criador à lista de membros

        // Formato: nomeDoGrupoComIcone;membro1,membro2,membro3
        String groupCreationContent = fullGroupName + ";" + String.join(",", allMembersForServer);
        Message groupCreateMsg = new Message(username, "Servidor", groupCreationContent, MessageType.GROUP_CREATE);
        client.sendMessage(groupCreateMsg);
        
        // Adiciona localmente para feedback, servidor confirmará.
        if (!gruposParticipando.contains(fullGroupName)) gruposParticipando.add(fullGroupName);
        if (!userModel.contains(fullGroupName)) userModel.addElement(fullGroupName);
        historicoMensagens.putIfAbsent(fullGroupName, new ArrayList<>());
        atualizarListaContatosComNotificacao();
        showChatView(fullGroupName); // Abre o chat do grupo recém-criado
    }

    // Lógica para sair de um grupo
    private void leaveGroup() {
        if (currentChat != null && currentChat.startsWith(GROUP_ICON_PREFIX)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Tem certeza que deseja sair do grupo '" + currentChat.replace(GROUP_ICON_PREFIX, "") + "'?",
                    "Sair do Grupo",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                Message leaveGroupMsg = new Message(username, currentChat, "LEAVE", MessageType.LEAVE_GROUP);
                client.sendMessage(leaveGroupMsg);
                // A UI será atualizada quando o servidor confirmar via GROUP_REMOVED_NOTIFICATION ou USER_LIST
            }
        }
    }

    // Envia uma mensagem de texto
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            MessageType type = currentChat.startsWith(GROUP_ICON_PREFIX) ? MessageType.GROUP : MessageType.PRIVATE;
            String receiver = currentChat; 
            
            Message msg = new Message(username, receiver, text, type);
            msg.setStatus(MessageStatus.SENDING); // Define status inicial

            client.sendMessage(msg); 

            // Adiciona mensagem à GUI e ao histórico local
            historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(msg);
            addMessageToPanel(msg, true); // true = isOwnMessage
            inputField.setText("");
            scrollToBottom();
        }
    }

    // Envia um arquivo
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
                if (fileData.length > 20 * 1024 * 1024) { // Limite de 20MB (exemplo)
                    showError("O arquivo é muito grande (limite de 20MB).");
                    return;
                }
                
                MessageType type = currentChat.startsWith(GROUP_ICON_PREFIX) ? MessageType.GROUP : MessageType.PRIVATE;
                String receiver = currentChat;
                String fileMessageContent = "Arquivo: " + fileName + " (" + fileData.length / 1024 + " KB)";

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
    
    // Manipula mensagens recebidas do servidor
    public void handleMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            String chatKey; 

            switch (msg.getType()) {
                case USER_LIST:
                    // Atualiza a lista de usuários e grupos visíveis
                    List<String> receivedItems = new ArrayList<>(Arrays.asList(msg.getContent().split(",")));
                    userModel.clear(); 
                    // Adiciona usuários online (exceto o próprio)
                    receivedItems.stream()
                        .filter(item -> !item.trim().isEmpty() && !item.equals(username) && !item.startsWith(GROUP_ICON_PREFIX))
                        .forEach(userModel::addElement);
                    // Adiciona grupos dos quais este usuário faz parte (o servidor deve enviar apenas esses)
                    // ou todos os grupos, e o cliente filtra.
                    // Por simplicidade, assumimos que o servidor envia uma lista combinada de usuários e grupos relevantes.
                    receivedItems.stream()
                        .filter(item -> item.startsWith(GROUP_ICON_PREFIX))
                        .forEach(groupName -> {
                            if (!userModel.contains(groupName)) userModel.addElement(groupName);
                            if (!gruposParticipando.contains(groupName)) gruposParticipando.add(groupName); // Mantém a lista local sincronizada
                        });
                    
                    // Remove grupos locais que não vieram mais na lista do servidor (se o usuário saiu/foi removido)
                    List<String> gruposAtuaisNaLista = new ArrayList<>();
                    for(int i=0; i<userModel.size(); i++){
                        if(userModel.getElementAt(i).startsWith(GROUP_ICON_PREFIX)){
                            gruposAtuaisNaLista.add(userModel.getElementAt(i));
                        }
                    }
                    gruposParticipando.retainAll(gruposAtuaisNaLista); // Mantém apenas os grupos que ainda estão na lista visual

                    atualizarListaContatosComNotificacao();
                    break;

                case PRIVATE:
                case GROUP: 
                    boolean isOwnMessage = msg.getSender().equals(username);
                    
                    if (msg.getType() == MessageType.GROUP) {
                        chatKey = msg.getReceiver(); // Para msg de grupo, receiver é o nome do grupo
                    } else { // PRIVATE
                        chatKey = isOwnMessage ? msg.getReceiver() : msg.getSender(); // Chave é o outro participante
                    }

                    // Tratar arquivos recebidos
                    if (msg.getFileData() != null && msg.getFileName() != null && !isOwnMessage) {
                        // Salvar automaticamente ou apenas notificar para download manual?
                        // A lógica atual já inclui um botão de download no addMessageToPanel.
                        // Podemos alterar o conteúdo da mensagem para indicar que é um arquivo.
                        msg.setContent("Arquivo: " + msg.getFileName() + " (" + msg.getFileData().length / 1024 + " KB)");
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
                    if (!gruposParticipando.contains(newGroupName)) {
                        gruposParticipando.add(newGroupName);
                        if (!userModel.contains(newGroupName)) { 
                            userModel.addElement(newGroupName);
                        }
                        historicoMensagens.putIfAbsent(newGroupName, new ArrayList<>());
                        // Notificar se este cliente foi adicionado (não se ele criou)
                        if (msg.getSender().equals("Servidor") && msg.getReceiver().equals(username)) {
                             JOptionPane.showMessageDialog(this, "Você foi adicionado ao grupo: " + newGroupName.replace(GROUP_ICON_PREFIX,""), "Novo Grupo", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                    atualizarListaContatosComNotificacao();
                    break;
                
                case GROUP_REMOVED_NOTIFICATION: // Servidor notificou que o usuário foi removido/saiu de um grupo
                    String groupToRemove = msg.getContent(); // Nome do grupo
                    gruposParticipando.remove(groupToRemove);
                    userModel.removeElement(groupToRemove); // Remove da lista visual
                    historicoMensagens.remove(groupToRemove); // Opcional: remover histórico local
                    notificacoes.remove(groupToRemove);
                    if (isInChatView && currentChat.equals(groupToRemove)) {
                        showContactsView(); // Volta para a lista de contatos
                        JOptionPane.showMessageDialog(this, "Você não está mais no grupo: " + groupToRemove.replace(GROUP_ICON_PREFIX,""), "Grupo Deixado", JOptionPane.INFORMATION_MESSAGE);
                    }
                    atualizarListaContatosComNotificacao();
                    break;

                case TEXT: // Mensagens de sistema do servidor
                    if (msg.getSender().equals("Servidor")) { 
                        JOptionPane.showMessageDialog(this, msg.getContent(), "Mensagem do Servidor", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;

                default:
                    System.out.println("Tipo de mensagem não tratado recebido na GUI: " + msg.getType());
            }
        });
    }
    
    // Atualiza a lista de contatos na UI, aplicando ícones de notificação
    private void atualizarListaContatosComNotificacao() {
        // Coleta todos os elementos atuais da userModel e da lista de gruposParticipando
        Set<String> todosOsItens = new HashSet<>();
        for (int i = 0; i < userModel.getSize(); i++) {
            todosOsItens.add(userModel.getElementAt(i).replace(NOTIFICATION_ICON, "").trim());
        }
        todosOsItens.addAll(gruposParticipando); // Garante que todos os grupos que o usuário participa estejam na lista

        userModel.clear();
        List<String> sortedItems = new ArrayList<>(todosOsItens);
        // Opcional: Ordenar (ex: usuários primeiro, depois grupos, ou alfabeticamente)
        // Collections.sort(sortedItems); 

        for (String item : sortedItems) {
            String nameWithoutIcon = item.replace(NOTIFICATION_ICON, "").trim();
            if (notificacoes.getOrDefault(nameWithoutIcon, false)) {
                userModel.addElement(nameWithoutIcon + NOTIFICATION_ICON);
            } else {
                userModel.addElement(nameWithoutIcon);
            }
        }
    }

    // Mostra uma mensagem de erro
    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }
    
    // Classe interna para bordas arredondadas (sem alterações)
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

    // Método Main
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