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
                log("INFO", "SISTEMA_SHUTDOWN", "Iniciando desligamento do servidor...");
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
                        clientSocket.close();
                        break;
                    }
                    log("INFO", "CONEXÃO_NOVA", "Nova conexão recebida de: " + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientExecutorService.submit(handler);
                } catch (SocketException e) {
                    if (!running) {
                        log("INFO", "SISTEMA_SOCKET", "ServerSocket fechado durante o desligamento.");
                    } else {
                        logError("ACEITAR_CONEXAO_SOCKET", "SocketException ao aceitar conexão", e);
                    }
                } catch (IOException e) {
                    if (running) {
                        logError("ACEITAR_CONEXAO_IO", "Erro de I/O ao aceitar nova conexão", e);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                logError("SISTEMA_STARTUP_FATAL", "Erro crítico ao iniciar o servidor na porta " + PORT, e);
                JOptionPane.showMessageDialog(this, "Erro crítico ao iniciar servidor: " + e.getMessage() + "\nVerifique se a porta " + PORT + " está disponível.", "Erro Servidor", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        } finally {
            log("INFO", "SISTEMA_LOOP_END", "Loop principal do servidor terminado.");
        }
    }

    private void shutdownServer() {
        if (!running) return; // Evita múltiplas chamadas de shutdown
        running = false;

        log("INFO", "SHUTDOWN_SOCKET", "Fechando ServerSocket...");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logError("SHUTDOWN_SOCKET_IO", "Erro ao fechar ServerSocket", e);
            }
        }

        log("INFO", "SHUTDOWN_CLIENTS", "Desconectando clientes e parando handlers...");
        new ArrayList<>(clients.values()).forEach(ClientHandler::closeClientSocket);
        clients.clear();

        log("INFO", "SHUTDOWN_EXECUTOR", "Desligando pool de threads dos clientes...");
        if (clientExecutorService != null) {
            clientExecutorService.shutdown();
            try {
                if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientExecutorService.shutdownNow();
                    if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS))
                        logError("SHUTDOWN_EXECUTOR_TERMINATE", "Pool de threads não terminou", null);
                }
            } catch (InterruptedException ie) {
                clientExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log("INFO", "SISTEMA_SHUTDOWN_COMPLETO", "Servidor desligado. Encerrando aplicação.");
        dispose();
        System.exit(0);
    }

    public synchronized boolean addClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) {
            log("AVISO", "ADD_CLIENT_FALHA", "Tentativa de adicionar usuário '" + username + "' que já existe.");
            return false; 
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
            groups.forEach((groupName, members) -> {
                boolean removed = members.remove(username);
                if (removed) {
                    log("INFO", "GRUPO_MEMBRO_REM", "Usuário " + username + " removido do grupo " + groupName);
                }
            });
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
    
    public synchronized void sendPrivateMessage(Message msg, String senderUsername) {
        if (!running) return;
        ClientHandler receiverHandler = clients.get(msg.getReceiver());
        ClientHandler senderHandler = clients.get(senderUsername);

        if (receiverHandler != null) {
            receiverHandler.sendMessage(msg); 
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
        if (!running) return;
        String groupName = msgFromSender.getReceiver();
        List<String> members = groups.get(groupName);
        ClientHandler originalSenderHandler = clients.get(senderUsername);

        if (members != null && !members.isEmpty()) {
            Message relayedMsg = new Message(msgFromSender.getMessageId(), senderUsername, groupName, msgFromSender.getContent(), MessageType.GROUP);
            relayedMsg.setTimestamp(msgFromSender.getTimestamp());
            if (msgFromSender.getFileData() != null && msgFromSender.getFileName() != null) {
                relayedMsg.setFileData(msgFromSender.getFileData());
                relayedMsg.setFileName(msgFromSender.getFileName());
            }
            
            int deliveryCount = 0;
            for (String memberUsername : members) {
                ClientHandler memberHandler = clients.get(memberUsername);
                if (memberHandler != null) {
                    if (!memberUsername.equals(senderUsername)) {
                        memberHandler.sendMessage(relayedMsg);
                        deliveryCount++;
                    }
                }
            }

            if (originalSenderHandler != null) {
                if (deliveryCount > 0 || (members.size() == 1 && members.contains(senderUsername))) { 
                    notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.DELIVERED, groupName, new Date());
                } else if (members.size() > 1) { 
                     notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.FAILED, groupName, new Date());
                }
            }
        } else {
            log("AVISO", "GRUPO_MSG_FALHA", "Grupo " + groupName + " não encontrado ou vazio para msg de " + senderUsername);
             if (originalSenderHandler != null) {
                notifyMessageStatus(senderUsername, msgFromSender.getMessageId(), MessageStatus.FAILED, groupName, new Date());
            }
        }
    }
    
    public synchronized void createGroup(String groupNameWithIcon, List<String> membersUsernames, String creatorUsername) {
        if (!running) return;
        String cleanGroupName = groupNameWithIcon.startsWith("\uD83D\uDC65 ") ? groupNameWithIcon.substring(2).trim() : groupNameWithIcon.trim();

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
            } else {
                log("AVISO", "GRUPO_CRIA_MEMBRO_INV", "Membro " + memberName + " não encontrado/offline ao criar grupo " + cleanGroupName);
            }
        }
        
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
        for (String memberName : validMembers) {
            ClientHandler memberHandler = clients.get(memberName);
            if (memberHandler != null) {
                groupCreatedNotification.setReceiver(memberName); 
                memberHandler.sendMessage(groupCreatedNotification);
            }
        }
        broadcastUserList();
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
                try {
                    logArea.replaceRange("", 0, 15000);
                } catch (Exception e) { /*ignore*/ }
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
            shortStackTrace = stackLines[0] + (stackLines.length > 1 ? " " + stackLines[1] : ""); // Atribuição correta
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