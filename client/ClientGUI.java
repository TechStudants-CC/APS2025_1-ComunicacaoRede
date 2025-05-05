package client;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private Map<String, List<String>> historicoMensagens = new HashMap<>();
    private Map<String, Boolean> notificacoes = new HashMap<>();
    
    // Cores
    private final Color primaryColor = new Color(45, 62, 80);
    private final Color secondaryColor = new Color(52, 152, 219);
    private final Color accentColor = new Color(46, 204, 113);
    private final Color background = new Color(34, 47, 62);
    private final Color textColor = new Color(236, 240, 241);
    private final Color panelColor = new Color(44, 62, 80);
    
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
            
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setBorder(new EmptyBorder(8, 15, 8, 15));
            
            if (notificacoes.getOrDefault(contact, false)) {
                label.setText(contact + " 🔴");
                label.setForeground(accentColor);
            } else {
                label.setText(contact);
                label.setForeground(textColor);
            }
            
            label.setBackground(isSelected ? new Color(52, 73, 94) : panelColor);
            return label;
        }
    }

    public ClientGUI() {
        configureLookAndFeel();
        showCustomLoginDialog();
        if (username != null) setupInterface();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.put("Button.foreground", textColor);
            UIManager.put("Button.background", secondaryColor);
            UIManager.put("Button.focus", new Color(0,0,0,0));
            UIManager.put("Button.border", new RoundBorder(20, secondaryColor));
            UIManager.put("TextField.border", new RoundBorder(new Color(149, 165, 166), 20));
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private void showCustomLoginDialog() {
        JDialog loginDialog = new JDialog(this, "Entrar no Chat", true);
        loginDialog.setLayout(new GridBagLayout());
        loginDialog.setUndecorated(true);
        loginDialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);

        // Painel principal
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(background);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // Componentes
        JLabel titleLabel = new JLabel("Chat Ambiental");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(textColor);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField nameField = new JTextField(15);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameField.setForeground(textColor);
        nameField.setBackground(new Color(60, 60, 60));
        nameField.setBorder(new RoundBorder(20, new Color(100, 100, 100)));
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setMaximumSize(new Dimension(200, 40));
        
        // Capitalização automática
        ((AbstractDocument) nameField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) 
                throws BadLocationException {
                if (offset == 0 && text.length() > 0) {
                    text = text.substring(0, 1).toUpperCase() + text.substring(1);
                }
                super.replace(fb, offset, length, text, attrs);
            }
        });

        JButton enterButton = new JButton("Entrar");
        enterButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        enterButton.setForeground(textColor);
        enterButton.setBackground(secondaryColor);
        enterButton.setBorder(new RoundBorder(20, secondaryColor));
        enterButton.setFocusPainted(false);
        enterButton.setContentAreaFilled(false);
        enterButton.setOpaque(true);
        enterButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enterButton.setPreferredSize(new Dimension(120, 40));
        enterButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Efeitos
        enterButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                enterButton.setBackground(secondaryColor.brighter());
            }
            public void mouseExited(MouseEvent evt) {
                enterButton.setBackground(secondaryColor);
            }
        });

        enterButton.addActionListener(e -> {
            username = nameField.getText().trim();
            if (!username.isEmpty()) {
                try {
                    client = new Client("127.0.0.1", 54321, username, ClientGUI.this);
                    loginDialog.dispose();
                } catch (IOException ex) {
                    showError("Falha na conexão.");
                    System.exit(1);
                }
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Digite um nome válido!", "Erro", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        // Layout
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(nameField);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        mainPanel.add(enterButton);

        loginDialog.add(mainPanel);
        loginDialog.pack();
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);
    }

    private void setupInterface() {
        setTitle("Chat Ambiental - " + username);
        setSize(360, 640);
        setLayout(new BorderLayout());
        getContentPane().setBackground(background);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        createContactsPanel();
        createChatPanel();

        mainPanel.add(contactsPanel, "contacts");
        mainPanel.add(chatPanel, "chat");

        add(mainPanel, BorderLayout.CENTER);
        setVisible(true);
    }

    private void createContactsPanel() {
        contactsPanel = new JPanel(new BorderLayout());
        contactsPanel.setBackground(background);
        contactsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Cabeçalho
        JLabel header = new JLabel("CONTATOS ONLINE", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.setForeground(textColor);
        header.setBorder(new EmptyBorder(10, 0, 10, 0));
        contactsPanel.add(header, BorderLayout.NORTH);

        // Lista
        userModel = new DefaultListModel<>();
        userList = new JList<>(userModel);
        userList.setCellRenderer(new ContactListRenderer());
        userList.setBackground(panelColor);
        userList.setBorder(new RoundBorder(10, panelColor));
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null && !selected.equals(username)) {
                        String contact = selected.replace(" 🔴", "");
                        notificacoes.put(contact, false);
                        atualizarListaContatos();
                        showChatView(contact);
                    }
                }
            }
        });
        
        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(null);
        contactsPanel.add(scroll, BorderLayout.CENTER);

        // Rodapé
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(background);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(new EmptyBorder(10, 0, 5, 0));
        
        JButton btnGroup = styledButton("NOVO GRUPO", secondaryColor, 14);
        btnGroup.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnGroup.addActionListener(e -> createGroup());
        
        JLabel info = new JLabel("Toque duplo para abrir chat");
        info.setForeground(new Color(149, 165, 166));
        info.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        bottomPanel.add(btnGroup);
        bottomPanel.add(Box.createVerticalStrut(8));
        bottomPanel.add(info);
        
        contactsPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(background);
        chatPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Cabeçalho
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(primaryColor);
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.setPreferredSize(new Dimension(0, 45));
        
        JButton btnBack = styledButton("←", new Color(149, 165, 166), 14);
        btnBack.setPreferredSize(new Dimension(50, 30));
        btnBack.addActionListener(e -> showContactsView());
        
        JLabel title = new JLabel("", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(textColor);
        
        header.add(btnBack, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        chatPanel.add(header, BorderLayout.NORTH);

        // Área de chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatArea.setForeground(textColor);
        chatArea.setBackground(panelColor);
        chatArea.setBorder(new RoundBorder(10, panelColor));
        
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(null);
        chatPanel.add(scroll, BorderLayout.CENTER);

        // Entrada e botões
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(background);
        inputPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setForeground(textColor);
        inputField.setBackground(panelColor);
        inputField.setBorder(new RoundBorder(15, new Color(149, 165, 166)));
        inputField.addActionListener(e -> sendMessage());
        
        JButton btnSend = styledButton("Enviar", accentColor, 13);
        btnSend.setPreferredSize(new Dimension(80, 30));
        btnSend.addActionListener(e -> sendMessage());
        
        JButton btnFile = styledButton("Arquivo", secondaryColor, 13);
        btnFile.setPreferredSize(new Dimension(80, 30));
        btnFile.addActionListener(e -> sendFile());
        
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonsPanel.setBackground(background);
        buttonsPanel.add(btnFile);
        buttonsPanel.add(btnSend);
        
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private JButton styledButton(String text, Color color, float fontSize) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, (int)fontSize));
        btn.setForeground(textColor);
        btn.setBackground(color);
        btn.setBorder(new RoundBorder(15, color));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setMargin(new Insets(5, 10, 5, 10));
        
        // Adicionar efeito hover
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                btn.setBackground(color.brighter());
            }
            public void mouseExited(MouseEvent evt) {
                btn.setBackground(color);
            }
        });
        
        return btn;
    }

    private static class RoundBorder extends AbstractBorder {
        private int radius;
        private Color color;

        public RoundBorder(Color color, int radius) {
            this.radius = radius;
            this.color = color;
        }

        public RoundBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius/2, radius/2, radius/2, radius/2);
        }
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

    private void showChatView(String title) {
        currentChat = title;
        // Atualizar o título no cabeçalho
        ((JLabel) ((BorderLayout) ((JPanel) chatPanel.getComponent(0)).getLayout()).getLayoutComponent(BorderLayout.CENTER)).setText(title);
        chatArea.setText("");
        historicoMensagens.getOrDefault(title, new ArrayList<>()).forEach(chatArea::append);
        notificacoes.put(title, false);
        atualizarListaContatos();
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "chat");
    }

    private void showContactsView() {
        ((CardLayout) mainPanel.getLayout()).show(mainPanel, "contacts");
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            Message msg;
            String formattedMessage = "[Você]: " + text + "\n";
            
            if (currentChat.contains("🧑‍🤝‍🧑")) {
                String grupo = currentChat.replace("🧑‍🤝‍🧑 ", "");
                msg = new Message(username, grupo, text, MessageType.GROUP);
                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(formattedMessage);
            } else {
                msg = new Message(username, currentChat, text, MessageType.PRIVATE);
                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>()).add(formattedMessage);
            }
            
            client.sendMessage(msg);
            chatArea.append(formattedMessage);
            inputField.setText("");
        }
    }
    
    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecione um arquivo");
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileData = Files.readAllBytes(file.toPath());
                Message fileMessage = new Message(username, currentChat, "Enviando arquivo: " + file.getName(), MessageType.FILE);
                fileMessage.setFileData(fileData);
                fileMessage.setFileName(file.getName());
                client.sendMessage(fileMessage);
                
                historicoMensagens.computeIfAbsent(currentChat, k -> new ArrayList<>())
                    .add("[Você enviou um arquivo]: " + file.getName() + "\n");
                chatArea.append("[Você enviou um arquivo]: " + file.getName() + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                showError("Erro ao enviar o arquivo.");
            }
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
                    historicoMensagens.computeIfAbsent(senderPriv, k -> new ArrayList<>()).add(contentPriv);
                    
                    if (senderPriv.equals(currentChat)) {
                        chatArea.append(contentPriv);
                    } else {
                        notificacoes.put(senderPriv, true);
                        atualizarListaContatos();
                    }
                    break;
                    
                case GROUP:
                    String groupName = msg.getReceiver();
                    String senderGroup = msg.getSender();
                    String contentGroup = "[" + senderGroup + "]: " + msg.getContent() + "\n";
                    historicoMensagens.computeIfAbsent(groupName, k -> new ArrayList<>()).add(contentGroup);
                    
                    if (groupName.equals(currentChat)) {
                        chatArea.append(contentGroup);
                    } else {
                        notificacoes.put(groupName, true);
                        atualizarListaContatos();
                    }
                    break;

                case GROUP_CREATE:
                    String newGroupName = msg.getContent();
                    if (!grupos.contains(newGroupName)) {
                        grupos.add(newGroupName);
                        userModel.addElement(newGroupName);
                    }
                    break;
                    
                case FILE:
                    try {
                        String nomeRemetente = msg.getSender();
                        String nomeArquivo = msg.getFileName();
                        byte[] dadosArquivo = msg.getFileData();
                        
                        File dirDownloads = new File("downloads");
                        if (!dirDownloads.exists()) {
                            dirDownloads.mkdir();
                        }
                        
                        File arquivo = new File(dirDownloads, nomeArquivo);
                        Files.write(arquivo.toPath(), dadosArquivo);
                        String caminhoCompleto = arquivo.getAbsolutePath();
                        
                        String conteudoArquivo = "[Arquivo recebido de " + nomeRemetente + "]: " + nomeArquivo +
                                " (salvo em: " + caminhoCompleto + ")\n";
                                
                        historicoMensagens.computeIfAbsent(nomeRemetente, k -> new ArrayList<>()).add(conteudoArquivo);
                        
                        if (nomeRemetente.equals(currentChat)) {
                            chatArea.append(conteudoArquivo);
                        } else {
                            notificacoes.put(nomeRemetente, true);
                            atualizarListaContatos();
                        }
                        
                        JOptionPane.showMessageDialog(this,
                                "Arquivo salvo em: " + caminhoCompleto,
                                "Arquivo Recebido",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException e) {
                        showError("Erro ao salvar o arquivo recebido.");
                        e.printStackTrace();
                    }
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

    public void showError(String error) {
        JOptionPane.showMessageDialog(this, error, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}