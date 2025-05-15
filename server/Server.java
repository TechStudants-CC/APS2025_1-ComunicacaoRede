// server/Server.java
package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import common.Message;
import common.MessageType;
import common.MessageStatus;

public class Server extends JFrame {
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final int PORT = 54321;
    private volatile boolean running = false;
    private ExecutorService clientExecutorService;

    // Define o prefixo do ícone do grupo aqui, igual ao usado no ClientGUI
    private static final String GROUP_ICON_PREFIX_SERVER_KNOWLEDGE = "\uD83D\uDC65 ";


    public Server() {
        setTitle("Servidor de Chat - Logs");
        setSize(750, 550);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        logArea.setMargin(new Insets(5,5,5,5));
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log("INFO", "SISTEMA_SHUTDOWN_REQ", "Requisição de desligamento do servidor...");
                shutdownServer();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        setVisible(true);
        new Thread(this::startServer).start();
    }

    private void startServer() {
        clientExecutorService = Executors.newCachedThreadPool();
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            log("INFO", "SISTEMA_INIT", "Servidor iniciado na porta " + PORT + ".");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (!running) {
                        try { clientSocket.close(); } catch (IOException ex) {/*ignore*/}
                        break;
                    }
                    log("INFO", "CONEXÃO_NOVA", "Nova conexão de: " + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientExecutorService.submit(handler);
                } catch (SocketException e) {
                    if (!running) { /* Normal durante shutdown */ } 
                    else { logError("ACEITAR_CONEXAO_SOCKET", "SocketException ao aceitar conexão", e); }
                } catch (IOException e) {
                    if (running) { logError("ACEITAR_CONEXAO_IO", "Erro de I/O ao aceitar nova conexão", e); }
                }
            }
        } catch (IOException e) {
            if (running) {
                logError("SISTEMA_STARTUP_FATAL", "Erro crítico ao iniciar servidor na porta " + PORT, e);
                JOptionPane.showMessageDialog(this, "Erro crítico: " + e.getMessage(), "Erro Servidor", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        } finally {
            log("INFO", "SISTEMA_LOOP_END", "Loop principal do servidor terminado.");
        }
    }

    private synchronized void shutdownServer() {
        if (!running) return;
        running = false;
        log("INFO", "SHUTDOWN_PROCESSO", "Iniciando processo de desligamento do servidor...");

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log("INFO", "SHUTDOWN_SOCKET_SRV", "ServerSocket fechado.");
            } catch (IOException e) {
                logError("SHUTDOWN_SOCKET_SRV_IO", "Erro ao fechar ServerSocket", e);
            }
        }
        
        log("INFO", "SHUTDOWN_HANDLERS", "Fechando conexões de cliente...");
        new ArrayList<>(clients.values()).forEach(ClientHandler::closeClientSocket);
        
        log("INFO", "SHUTDOWN_EXECUTOR", "Desligando pool de threads dos clientes...");
        if (clientExecutorService != null) {
            clientExecutorService.shutdown();
            try {
                if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientExecutorService.shutdownNow();
                    if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS))
                        logError("SHUTDOWN_EXECUTOR_FAIL", "Pool de threads não terminou", null);
                }
            } catch (InterruptedException ie) {
                clientExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        clients.clear(); 
        groups.clear();
        log("INFO", "SISTEMA_SHUTDOWN_COMP", "Servidor desligado. Encerrando GUI.");
        dispose();
        System.exit(0);
    }

    public synchronized boolean addClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) {
            log("AVISO", "ADD_CLIENT_DUP", "Usuário '" + username + "' já conectado. Nova conexão rejeitada.");
            return false; 
        }
        clients.put(username, handler);
        log("INFO", "ADD_CLIENT_OK", "Conectado: " + username + " (" + handler.getRemoteSocketAddress() + ")");
        broadcastUserList();
        return true;
    }

    public synchronized void removeClient(String username) {
        if (username == null) return;
        ClientHandler removedHandler = clients.remove(username);
        if (removedHandler != null) {
            log("INFO", "REMOVE_CLIENT", "Desconectado: " + username);
            List<String> groupsModified = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                if (entry.getValue().remove(username)) {
                    log("INFO", "GRUPO_MEMBRO_SAIU_OFF", username + " removido do grupo " + entry.getKey() + " (offline)");
                    groupsModified.add(entry.getKey());
                    if (entry.getValue().isEmpty()) {
                        // Opcional: groups.remove(entry.getKey());
                        // log("INFO", "GRUPO_VAZIO_AUTO_DEL", "Grupo " + entry.getKey() + " ficou vazio e foi removido.");
                    }
                }
            }
            broadcastUserList(); 
        }
    }
    
    public synchronized String getUserListString() {
        Set<String> userAndGroupNames = new HashSet<>(clients.keySet());
        userAndGroupNames.addAll(groups.keySet());
        return String.join(",", userAndGroupNames);
    }

    private synchronized void broadcastUserList() {
        if (!running) return;
        String userListStr = getUserListString();
        Message userListMsg = new Message("Servidor", "TODOS", userListStr, MessageType.USER_LIST);
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(userListMsg);
        }
    }
    
    public synchronized void routeMessage(Message msg, String senderUsername) {
        if (!running) return;
        
        ClientHandler senderHandler = clients.get(senderUsername);
        if (senderHandler == null) {
            log("AVISO", "ROTA_MSG_SENDER_NF", "Remetente " + senderUsername + " não encontrado para rotear mensagem.");
            return;
        }

        if (msg.getType() == MessageType.PRIVATE) {
            ClientHandler receiverHandler = clients.get(msg.getReceiver());
            if (receiverHandler != null) {
                receiverHandler.sendMessage(msg); 
                if (!senderUsername.equals(msg.getReceiver())) { 
                     notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.DELIVERED, msg.getReceiver(), new Date());
                }
            } else {
                log("AVISO", "ROTA_PRIVADA_OFFLINE", "Destinatário " + msg.getReceiver() + " offline para msg de " + senderUsername);
                notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, msg.getReceiver(), new Date());
            }
        } else if (msg.getType() == MessageType.GROUP) {
            String groupName = msg.getReceiver();
            List<String> members = groups.get(groupName);

            if (members != null && !members.isEmpty()) {
                Message relayedMsg = new Message(msg.getMessageId(), senderUsername, groupName, msg.getContent(), MessageType.GROUP);
                relayedMsg.setTimestamp(msg.getTimestamp());
                if (msg.getFileData() != null && msg.getFileName() != null) {
                    relayedMsg.setFileData(msg.getFileData());
                    relayedMsg.setFileName(msg.getFileName());
                }
                
                int deliveryCount = 0;
                for (String memberUsername : members) {
                    if (!memberUsername.equals(senderUsername)) { 
                        ClientHandler memberHandler = clients.get(memberUsername);
                        if (memberHandler != null) {
                            memberHandler.sendMessage(relayedMsg);
                            deliveryCount++;
                        }
                    }
                }
                if (deliveryCount > 0 || (members.size() == 1 && members.contains(senderUsername))) {
                     notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.DELIVERED, groupName, new Date());
                } else if (members.size() > 1 && !members.contains(senderUsername)){ 
                     notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, groupName, new Date());
                }
                 log("INFO", "ROTA_GRUPO_ENVIADA", "Msg de " + senderUsername + " para grupo " + groupName + " encaminhada para " + deliveryCount + " membros.");
            } else {
                log("AVISO", "ROTA_GRUPO_FALHA", "Grupo " + groupName + " não encontrado ou vazio para msg de " + senderUsername);
                notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, groupName, new Date());
            }
        }
    }
    
    public synchronized void createGroup(String groupNameWithIcon, List<String> membersUsernames, String creatorUsername) {
        if (!running) return;
        // Usa a constante local do servidor para remover o prefixo e obter o nome limpo
        String cleanGroupName = groupNameWithIcon.startsWith(GROUP_ICON_PREFIX_SERVER_KNOWLEDGE) ? 
                                groupNameWithIcon.substring(GROUP_ICON_PREFIX_SERVER_KNOWLEDGE.length()).trim() : 
                                groupNameWithIcon.trim();

        if (groups.containsKey(groupNameWithIcon) || clients.containsKey(groupNameWithIcon)) {
            log("AVISO", "GRUPO_CRIA_EXISTENTE", "Tentativa de criar grupo com nome já existente: " + groupNameWithIcon);
            ClientHandler creatorHandler = clients.get(creatorUsername);
            if(creatorHandler != null) {
                creatorHandler.sendMessage(new Message("Servidor", creatorUsername, "Erro: Nome de grupo '" + cleanGroupName + "' já existe.", MessageType.TEXT));
            }
            return;
        }

        List<String> validMembers = new ArrayList<>();
        for(String memberName : membersUsernames){
            if(clients.containsKey(memberName)){ 
                if(!validMembers.contains(memberName)) { 
                    validMembers.add(memberName);
                }
            }
        }
        
        if(validMembers.size() < 2){
             log("AVISO", "GRUPO_CRIA_MEMBROS_INSUF", "Grupo '" + cleanGroupName + "' precisa de pelo menos 2 membros online.");
             ClientHandler creatorHandler = clients.get(creatorUsername);
             if(creatorHandler != null) {
                creatorHandler.sendMessage(new Message("Servidor", creatorUsername, "Erro: Grupo '" + cleanGroupName + "' precisa de pelo menos 2 membros online.", MessageType.TEXT));
            }
            return;
        }

        groups.put(groupNameWithIcon, new ArrayList<>(validMembers)); 
        log("INFO", "GRUPO_CRIADO_SUCESSO", "Grupo: " + groupNameWithIcon + " | Criador: " + creatorUsername + " | Membros: " + validMembers);

        Message groupCreatedNotification = new Message("Servidor", "", groupNameWithIcon, MessageType.GROUP_CREATE);
        for (String memberName : validMembers) {
            ClientHandler memberHandler = clients.get(memberName);
            if (memberHandler != null) {
                groupCreatedNotification.setReceiver(memberName); 
                memberHandler.sendMessage(groupCreatedNotification);
            }
        }
        broadcastUserList();
    }

    public synchronized void handleLeaveGroup(String groupName, String usernameLeaving) {
        if (!running) return;
        List<String> members = groups.get(groupName);
        ClientHandler userLeavingHandler = clients.get(usernameLeaving);

        if (members != null && userLeavingHandler != null) {
            boolean removed = members.remove(usernameLeaving);
            if (removed) {
                log("INFO", "GRUPO_SAIDA", usernameLeaving + " saiu do grupo " + groupName);
                userLeavingHandler.sendMessage(new Message("Servidor", usernameLeaving, groupName, MessageType.GROUP_REMOVED_NOTIFICATION));
                
                if (members.isEmpty()) {
                    groups.remove(groupName);
                    log("INFO", "GRUPO_AUTO_DELETE", "Grupo " + groupName + " ficou vazio e foi removido.");
                }
                broadcastUserList();
            } else {
                log("AVISO", "GRUPO_SAIDA_FALHA_MEM", usernameLeaving + " tentou sair do grupo " + groupName + " mas não era membro.");
            }
        } else {
            log("AVISO", "GRUPO_SAIDA_FALHA_NGU", "Grupo " + groupName + " ou usuário " + usernameLeaving + " não encontrado para saída.");
        }
    }

    public void notifyMessageStatus(String userToNotify, String messageId, MessageStatus status, String relatedInfo, Date eventTimestamp) {
        if (!running && status != MessageStatus.FAILED) return;
        ClientHandler handlerToNotify = clients.get(userToNotify);
        if (handlerToNotify != null) {
            String statusContent = String.format("%s:%s:%s:%d", 
                                                 messageId, 
                                                 status.name(), 
                                                 (relatedInfo != null ? relatedInfo : ""),
                                                 eventTimestamp.getTime());

            Message statusUpdateMsg = new Message("Servidor", userToNotify, statusContent, MessageType.STATUS_UPDATE);
            handlerToNotify.sendMessage(statusUpdateMsg);
        }
    }

    public void log(String level, String category, String message) {
        if (logArea == null) {
            System.out.println(String.format("[%s] [%s] %s", level.toUpperCase(), category, message));
            return;
        }
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] [%-5s] [%-22s] %s", timestamp, level.toUpperCase(), category, message);
        SwingUtilities.invokeLater(() -> {
            if (logArea.getDocument().getLength() > 30000) { 
                try { logArea.replaceRange("", 0, 15000); } catch (Exception e) { /*ignore*/ }
            }
            logArea.append(logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); 
        });
    }

    public void logError(String category, String message, Throwable e) {
        String timestamp = dateFormat.format(new Date());
        String exceptionDetails = "";
        String shortStackTrace = ""; // Variável declarada aqui

        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String[] stackLines = sw.toString().split("\n");
            shortStackTrace = stackLines[0] + (stackLines.length > 1 ? " " + stackLines[1] : "");
            exceptionDetails = String.format(" | Exceção: %s (%s)", e.getClass().getSimpleName() +": "+ e.getMessage(), shortStackTrace);
        }
        
        String logMessage = String.format("[%s] [ERROR] [%-22s] %s%s",
                timestamp, category, message, exceptionDetails);
        
        if (logArea == null) {
            System.err.println(logMessage);
            if (e != null) e.printStackTrace();
            return;
        }
        SwingUtilities.invokeLater(() -> {
             if (logArea.getDocument().getLength() > 30000) { 
                try { logArea.replaceRange("", 0, 15000); } catch (Exception ex) { /*ignore*/ }
            }
            logArea.append(logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
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