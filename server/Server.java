// server/Server.java
package server;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List; // Explicit import
import java.util.concurrent.ConcurrentHashMap;
// import java.util.stream.Collectors; // Não usado diretamente aqui agora

import common.Message;
import common.MessageType;
import common.MessageStatus;

public class Server extends JFrame {
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>(); // Username -> ClientHandler
    private final ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>(); // GroupName -> List<Usernames>
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final int PORT = 54321;

    public Server() {
        setTitle("Servidor de Chat - Logs");
        setSize(750, 550); // Aumentado para melhor visualização dos logs
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13)); // Fonte monoespaçada para logs
        logArea.setMargin(new Insets(5,5,5,5));
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        setVisible(true);
        startServer();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            log("INFO", "SISTEMA_INIT", "Servidor iniciado na porta " + PORT + ".");
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log("INFO", "CONEXÃO_NOVA", "Nova conexão recebida de: " + clientSocket.getRemoteSocketAddress());
                    new ClientHandler(clientSocket, this).start();
                } catch (IOException e) {
                    logError("ACEITAR_CONEXAO", "Erro ao aceitar nova conexão de cliente", e);
                    // Continuar tentando aceitar outras conexões
                }
            }
        } catch (IOException e) {
            logError("SISTEMA_STARTUP", "Erro crítico ao iniciar o servidor na porta " + PORT, e);
            JOptionPane.showMessageDialog(this, "Erro crítico ao iniciar servidor: " + e.getMessage() + "\nVerifique se a porta " + PORT + " está disponível.", "Erro Servidor", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Encerra se não puder iniciar o servidor
        }
    }

    // Retorna false se o nome de usuário já estiver em uso
    public synchronized boolean addClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) {
            log("AVISO", "ADD_CLIENT_FALHA", "Tentativa de adicionar usuário '" + username + "' que já existe.");
            return false; // Nome de usuário já em uso
        }
        clients.put(username, handler);
        log("INFO", "ADD_CLIENT_SUCESSO", "Usuário conectado: " + username + " (" + handler.getRemoteSocketAddress().toString() + ")");
        broadcastUserList();
        return true;
    }


    public synchronized void removeClient(String username) {
        if (username == null) return;
        ClientHandler removedHandler = clients.remove(username);
        if (removedHandler != null) {
            log("INFO", "REMOVE_CLIENT", "Usuário desconectado: " + username);
            broadcastUserList();
            // Remover usuário de todos os grupos em que participa
            groups.forEach((groupName, members) -> {
                boolean removed = members.remove(username);
                if (removed) {
                    log("INFO", "GRUPO_MEMBRO_REM", "Usuário " + username + " removido do grupo " + groupName);
                    // Opcional: Se o grupo ficar vazio ou com apenas 1 membro, pode ser desfeito ou notificado.
                    if (members.isEmpty()) {
                        // groups.remove(groupName);
                        // log("INFO", "GRUPO_DESFEITO", "Grupo " + groupName + " ficou vazio e foi desfeito.");
                        // broadcastUserList(); // Atualizar lista se grupos são removidos
                    }
                }
            });
        }
    }
    
    public synchronized String getUserListString() {
        // Lista de usuários + lista de nomes de grupos
        Set<String> userAndGroupNames = new HashSet<>(clients.keySet());
        userAndGroupNames.addAll(groups.keySet());
        return String.join(",", userAndGroupNames);
    }


    private synchronized void broadcastUserList() {
        String userListStr = getUserListString();
        Message userListMsg = new Message("Servidor", "TODOS", userListStr, MessageType.USER_LIST);
        // log("INFO", "BROADCAST_USERLIST", "Transmitindo lista de usuários/grupos: " + userListStr);
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(userListMsg);
        }
    }
    
    public synchronized void sendPrivateMessage(Message msg, String senderUsername) {
        ClientHandler receiverHandler = clients.get(msg.getReceiver());
        ClientHandler senderHandler = clients.get(senderUsername);

        if (receiverHandler != null) {
            // Envia a mensagem original para o destinatário
            receiverHandler.sendMessage(msg); 
            // Notifica o remetente que a mensagem foi ENTREGUE (ao stream do destinatário)
            if (senderHandler != null && !senderUsername.equals(msg.getReceiver())) { 
                 notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.DELIVERED, msg.getReceiver(), new Date());
            }
        } else {
            log("AVISO", "PRIVADA_FALHA_OFFLINE", "Destinatário " + msg.getReceiver() + " não encontrado/offline para msg de " + senderUsername);
            if (senderHandler != null) {
                 notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, msg.getReceiver(), new Date());
            }
        }
    }
    
    public synchronized void sendGroupMessage(Message msgFromSender, String senderUsername) {
        String groupName = msgFromSender.getReceiver(); // O 'receiver' da mensagem original é o nome do grupo
        List<String> members = groups.get(groupName);
        ClientHandler originalSenderHandler = clients.get(senderUsername);

        if (members != null && !members.isEmpty()) {
            // Cria uma nova mensagem para retransmitir, mantendo o sender original e ID.
            // O receiver da mensagem retransmitida é o nome do grupo, para que o cliente saiba a origem.
            Message relayedMsg = new Message(msgFromSender.getMessageId(), senderUsername, groupName, msgFromSender.getContent(), MessageType.GROUP);
            relayedMsg.setTimestamp(msgFromSender.getTimestamp()); // Mantém o timestamp original
            if (msgFromSender.getFileData() != null && msgFromSender.getFileName() != null) {
                relayedMsg.setFileData(msgFromSender.getFileData());
                relayedMsg.setFileName(msgFromSender.getFileName());
            }
            
            int deliveryCount = 0;
            for (String memberUsername : members) {
                ClientHandler memberHandler = clients.get(memberUsername);
                if (memberHandler != null) {
                    if (!memberUsername.equals(senderUsername)) { // Não reenviar para o próprio remetente
                        memberHandler.sendMessage(relayedMsg);
                        deliveryCount++;
                    }
                } else {
                    log("AVISO", "GRUPO_MEMBRO_OFFLINE", "Membro " + memberUsername + " do grupo " + groupName + " está offline.");
                }
            }

            // Notifica o remetente original sobre o status da mensagem para o grupo
            if (originalSenderHandler != null) {
                if (deliveryCount > 0 || members.size() == 1 && members.contains(senderUsername)) { // Se entregue a alguém ou se o remetente é o único membro
                    notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.DELIVERED, groupName, new Date());
                } else if (members.size() > 1) { // Se há outros membros mas ninguém recebeu
                     notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.FAILED, groupName, new Date());
                }
            }
            log("INFO", "GRUPO_MSG_ENVIADA", String.format("Msg de %s para grupo %s encaminhada para %d/%d membros.", senderUsername, groupName, deliveryCount, members.size() - (members.contains(senderUsername) ? 1:0) ));

        } else {
            log("AVISO", "GRUPO_MSG_FALHA", "Grupo " + groupName + " não encontrado ou vazio para msg de " + senderUsername);
             if (originalSenderHandler != null) {
                notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.FAILED, groupName, new Date());
            }
        }
    }
    
    public synchronized void createGroup(String groupNameWithIcon, List<String> membersUsernames, String creatorUsername) {
        String cleanGroupName = groupNameWithIcon.startsWith("\uD83D\uDC65 ") ? groupNameWithIcon.substring(2).trim() : groupNameWithIcon.trim();

        if (groups.containsKey(groupNameWithIcon) || clients.containsKey(groupNameWithIcon)) { // Checa se nome de grupo já existe como usuário também
            log("AVISO", "GRUPO_CRIA_EXISTENTE", "Tentativa de criar grupo com nome já existente: " + groupNameWithIcon);
            ClientHandler creatorHandler = clients.get(creatorUsername);
            if(creatorHandler != null) {
                creatorHandler.sendMessage(new Message("Servidor", creatorUsername, "Erro: Nome de grupo '" + cleanGroupName + "' já existe.", MessageType.TEXT));
            }
            return;
        }

        List<String> validMembers = new ArrayList<>();
        for(String memberName : membersUsernames){ // membersUsernames já inclui o criador
            if(clients.containsKey(memberName)){ // Verifica se o membro está online/registrado
                if(!validMembers.contains(memberName)) { // Evita duplicados
                    validMembers.add(memberName);
                }
            } else {
                log("AVISO", "GRUPO_CRIA_MEMBRO_INV", "Membro " + memberName + " não encontrado/offline ao criar grupo " + cleanGroupName);
            }
        }
        
        // Um grupo precisa de pelo menos 2 membros (incluindo o criador)
        if(validMembers.size() < 2){
             log("AVISO", "GRUPO_CRIA_MEMBROS_INSUF", "Grupo '" + cleanGroupName + "' não pode ser criado. Pelo menos 2 membros online são necessários. Encontrados: " + validMembers.size());
             ClientHandler creatorHandler = clients.get(creatorUsername);
             if(creatorHandler != null) {
                creatorHandler.sendMessage(new Message("Servidor", creatorUsername, "Erro: Grupo '" + cleanGroupName + "' precisa de pelo menos 2 membros online.", MessageType.TEXT));
            }
            return;
        }

        groups.put(groupNameWithIcon, new ArrayList<>(validMembers)); 
        log("INFO", "GRUPO_CRIADO_SUCESSO", "Grupo: " + groupNameWithIcon + " | Criador: " + creatorUsername + " | Membros: " + validMembers);

        Message groupCreatedNotification = new Message("Servidor", "", groupNameWithIcon, MessageType.GROUP_CREATE);
        // O 'content' da notificação é o nome completo do grupo.
        // O 'sender' é "Servidor". O 'receiver' será cada membro.
        for (String memberName : validMembers) {
            ClientHandler memberHandler = clients.get(memberName);
            if (memberHandler != null) {
                groupCreatedNotification.setReceiver(memberName); 
                memberHandler.sendMessage(groupCreatedNotification);
            }
        }
        broadcastUserList(); // Atualiza a lista de "contatos" de todos, que agora inclui o novo grupo.
    }

    // Notifica um usuário (userToNotify) sobre o status de uma mensagem (messageId) que ele originalmente enviou.
    // relatedInfo pode ser o nome do destinatário que entregou/leu, ou o nome do grupo.
    public void notifyMessageStatus(String userToNotify, String messageId, MessageStatus status, String relatedInfo, Date eventTimestamp) {
        ClientHandler handlerToNotify = clients.get(userToNotify);
        if (handlerToNotify != null) {
            // Formato do conteúdo: "messageId:STATUS:relatedInfo:timestampMillis"
            String statusContent = String.format("%s:%s:%s:%d", 
                                                 messageId, 
                                                 status.name(), 
                                                 (relatedInfo != null ? relatedInfo : ""), // Evita "null" literal
                                                 eventTimestamp.getTime());

            Message statusUpdateMsg = new Message("Servidor", userToNotify, statusContent, MessageType.STATUS_UPDATE);
            // O timestamp da mensagem de STATUS_UPDATE em si é 'agora', mas o timestamp do evento é o eventTimestamp.
            // O cliente usará o eventTimestamp do payload.
            
            handlerToNotify.sendMessage(statusUpdateMsg);
            log("INFO", "NOTIFICAR_STATUS_MSG", String.format("Notificando %s: MsgID %s -> %s (Relacionado: %s)", userToNotify, messageId, status.name(), relatedInfo));
        } else {
             log("AVISO", "NOTIFICAR_STATUS_FALHA", "Não foi possível notificar " + userToNotify + " (offline?) sobre MsgID " + messageId);
        }
    }

    public void log(String level, String category, String message) {
        String timestamp = dateFormat.format(new Date());
        // Ajuste para alinhar melhor as categorias
        String logMessage = String.format("[%s] [%-5s] [%-22s] %s", timestamp, level.toUpperCase(), category, message);
        SwingUtilities.invokeLater(() -> {
            if (logArea.getDocument().getLength() > 20000) { // Limpa log antigo para não consumir muita memória
                try {
                    logArea.replaceRange("", 0, 10000);
                } catch (Exception e) { /*ignore*/ }
            }
            logArea.append(logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); 
        });
        // System.out.println(logMessage); // Opcional: duplicar no console
    }

    public void logError(String category, String message, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        // Pega as primeiras linhas do stack trace para concisão no log da GUI
        String[] stackLines = sw.toString().split("\n");
        String shortStackTrace = stackLines[0] + (stackLines.length > 1 ? " " + stackLines[1] : "");

        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] [ERROR] [%-22s] %s | Exceção: %s (%s)",
                timestamp, category, message, e.getClass().getSimpleName() +": "+ e.getMessage(), shortStackTrace);
        
        SwingUtilities.invokeLater(() -> {
             if (logArea.getDocument().getLength() > 20000) { 
                try { logArea.replaceRange("", 0, 10000); } catch (Exception ex) { /*ignore*/ }
            }
            logArea.append(logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.err.println(logMessage); // Log completo no System.err
        // e.printStackTrace(); // Para ver o stack trace completo no console
    }

    public String trimContent(String content) {
        if (content == null) return "<null>";
        return content.length() > 40 ? content.substring(0, 37) + "..." : content;
    }

    public static void main(String[] args) {
         try { 
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Não foi possível definir o LookAndFeel do sistema para o servidor: " + e.getMessage());
        }
        SwingUtilities.invokeLater(Server::new);
    }
}