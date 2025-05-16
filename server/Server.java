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
    private final ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>(); // Key: groupNameWithIcon
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final int PORT = 54321;
    private volatile boolean running = false;
    private ExecutorService clientExecutorService;

    public static final String GROUP_ICON_PREFIX = "\uD83D\uDC65 "; 

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
        return true;
    }

    public synchronized void removeClient(String username) {
        if (username == null) return;
        ClientHandler removedHandler = clients.remove(username);
        if (removedHandler != null) {
            log("INFO", "REMOVE_CLIENT", "Desconectado: " + username);
            // Notificar grupos que o usuário fazia parte
            List<String> groupsAffected = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                String groupNameWithIcon = entry.getKey();
                List<String> members = entry.getValue();
                if (members.remove(username)) {
                    log("INFO", "GRUPO_MEMBRO_SAIU_OFF", username + " removido do grupo " + groupNameWithIcon + " (offline)");
                    groupsAffected.add(groupNameWithIcon);
                    if (members.isEmpty()) {
                        // Não removeremos o grupo aqui, handleLeaveGroup fará isso se for uma saída explícita.
                        // Se o grupo ficar vazio devido a desconexão, ele simplesmente não será mais listado.
                        // Ou podemos decidir remover grupos vazios automaticamente:
                        // groups.remove(groupNameWithIcon);
                        // log("INFO", "GRUPO_AUTO_DELETE_VAZIO_OFF", "Grupo " + groupNameWithIcon + " ficou vazio (desconexão) e foi removido.");
                    } else {
                        // Notificar membros restantes sobre a saída (devido à desconexão)
                        String systemMessageContent = username + " saiu do grupo (desconectado).";
                        Message systemMessage = new Message("Servidor", groupNameWithIcon, systemMessageContent, MessageType.GROUP_SYSTEM_MESSAGE);
                        for (String member : members) {
                            ClientHandler memberHandler = clients.get(member);
                            if (memberHandler != null) {
                                memberHandler.sendMessage(systemMessage);
                            }
                        }
                    }
                }
            }
            broadcastUserList(); // Atualiza as listas de todos
        }
    }

    public synchronized String getUserListString(String forWhomUsername) {
        Set<String> itemsForThisUser = new HashSet<>();
        for (String clientName : clients.keySet()) {
            if (!clientName.equals(forWhomUsername)) {
                itemsForThisUser.add(clientName);
            }
        }
        for (Map.Entry<String, List<String>> groupEntry : groups.entrySet()) {
            if (groupEntry.getValue().contains(forWhomUsername)) {
                itemsForThisUser.add(groupEntry.getKey());
            }
        }
        return String.join(",", itemsForThisUser);
    }

    synchronized void broadcastUserList() {
        if (!running) return;
        log("INFO", "BROADCAST_USER_LIST", "Iniciando broadcast da lista de usuários/grupos para " + clients.size() + " clientes.");
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            if (handler != null && handler.getSocket() != null && !handler.getSocket().isClosed() && handler.getSocket().isConnected()) {
                String userSpecificListStr = getUserListString(username);
                Message userListMsg = new Message("Servidor", username, userSpecificListStr, MessageType.USER_LIST);
                handler.sendMessage(userListMsg);
            } else {
                log("AVISO", "BROADCAST_USER_LIST_SKIP", "Pulando envio para " + (username != null ? username : "handler nulo/socket fechado") + " durante broadcast.");
            }
        }
    }

    public synchronized void routeMessage(Message msg, String senderUsername) {
        if (!running) return;

        ClientHandler senderHandler = clients.get(senderUsername);
        if (senderHandler == null) {
            log("AVISO", "ROTA_MSG_SENDER_NF", "Remetente " + senderUsername + " não encontrado.");
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
            String groupNameWithIcon = msg.getReceiver();
            List<String> members = groups.get(groupNameWithIcon);

            if (members != null && members.contains(senderUsername)) {
                Message relayedMsg = new Message(msg.getMessageId(), senderUsername, groupNameWithIcon, msg.getContent(), MessageType.GROUP);
                relayedMsg.setTimestamp(msg.getTimestamp());
                if (msg.getFileData() != null && msg.getFileName() != null) {
                    relayedMsg.setFileData(msg.getFileData());
                    relayedMsg.setFileName(msg.getFileName());
                }

                int deliveryCount = 0;
                for (String memberUsername : members) {
                    if (!memberUsername.equals(senderUsername)) { // Não envia para o próprio remetente
                        ClientHandler memberHandler = clients.get(memberUsername);
                        if (memberHandler != null) {
                            memberHandler.sendMessage(relayedMsg);
                            deliveryCount++;
                        }
                    }
                }
                if (deliveryCount > 0 || (members.size() == 1 && members.contains(senderUsername))) {
                     notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.DELIVERED, groupNameWithIcon, new Date());
                } else if (members.size() > 1){ // Se há outros membros, mas nenhum online
                     log("INFO", "ROTA_GRUPO_DELIVERY_FAIL", "Msg de " + senderUsername + " para grupo " + groupNameWithIcon + ". Nenhum outro membro online para receber.");
                     notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.SENT, groupNameWithIcon, new Date()); // Marcado como enviado ao servidor
                }
                 log("INFO", "ROTA_GRUPO_ENVIADA", "Msg de " + senderUsername + " para grupo " + groupNameWithIcon + " encaminhada para " + deliveryCount + " membros.");
            } else if (members == null) {
                log("AVISO", "ROTA_GRUPO_FALHA_NE", "Grupo " + groupNameWithIcon + " não existe para msg de " + senderUsername);
                notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, groupNameWithIcon, new Date());
            } else { // Não é membro
                 log("AVISO", "ROTA_GRUPO_FALHA_NM", senderUsername + " não é membro do grupo " + groupNameWithIcon + ". Mensagem não enviada.");
                 Message notMemberMsg = new Message("Servidor", senderUsername, "Você não pode enviar mensagens para o grupo '" + groupNameWithIcon.replace(GROUP_ICON_PREFIX, "") + "' pois não é um membro.", MessageType.TEXT);
                 senderHandler.sendMessage(notMemberMsg);
                 notifyMessageStatus(senderUsername, msg.getMessageId(), MessageStatus.FAILED, groupNameWithIcon, new Date());
            }
        }
    }

    public synchronized void createGroup(String groupNameWithIcon, List<String> membersUsernames, String creatorUsername) {
        if (!running) return;
        String cleanGroupName = groupNameWithIcon.replace(GROUP_ICON_PREFIX, "").trim();

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
            if(clients.containsKey(memberName)){ // Só adiciona membros que estão online/válidos
                if(!validMembers.contains(memberName)) {
                    validMembers.add(memberName);
                }
            } else {
                 log("AVISO", "GRUPO_CRIA_MEMBRO_OFF", "Membro " + memberName + " não encontrado/offline ao criar grupo " + cleanGroupName);
            }
        }
        // Garante que o criador está na lista se for válido
        if (!validMembers.contains(creatorUsername) && clients.containsKey(creatorUsername)) {
            validMembers.add(0, creatorUsername); // Adiciona no início
        }


        if(validMembers.isEmpty()){
             log("AVISO", "GRUPO_CRIA_MEMBROS_INSUF", "Grupo '" + cleanGroupName + "' não pôde ser criado pois não há membros válidos online (incluindo o criador).");
             ClientHandler creatorHandler = clients.get(creatorUsername);
             if(creatorHandler != null) {
                creatorHandler.sendMessage(new Message("Servidor", creatorUsername, "Erro: Grupo '" + cleanGroupName + "' não pôde ser criado (sem membros válidos online).", MessageType.TEXT));
            }
            return;
        }

        groups.put(groupNameWithIcon, new ArrayList<>(validMembers));
        log("INFO", "GRUPO_CRIADO_SUCESSO", "Grupo: " + groupNameWithIcon + " | Criador: " + creatorUsername + " | Membros: " + validMembers);

        // Notifica o criador sobre a criação
        ClientHandler creatorHandler = clients.get(creatorUsername);
        if (creatorHandler != null) {
            String creatorMsgContent = "Você criou o grupo '" + cleanGroupName + "'.";
            creatorHandler.sendMessage(new Message("Servidor", groupNameWithIcon, creatorMsgContent, MessageType.GROUP_SYSTEM_MESSAGE));
        }
        
        // Notifica os membros (incluindo o criador pela GROUP_CREATE) que foram adicionados
        // E envia a mensagem de sistema para os outros membros
        String addedMsgContent = creatorUsername + " adicionou você ao grupo '" + cleanGroupName + "'.";
        if (validMembers.size() > 1) { // Se há outros membros além do criador
            addedMsgContent = creatorUsername + " criou o grupo '" + cleanGroupName + "' e adicionou você.";
        }

        for (String memberName : validMembers) {
            ClientHandler memberHandler = clients.get(memberName);
            if (memberHandler != null) {
                // Notificação de que o grupo foi criado e eles são membros (já faz isso com GROUP_CREATE)
                memberHandler.sendMessage(new Message("Servidor", memberName, groupNameWithIcon, MessageType.GROUP_CREATE));
                
                // Mensagem de sistema específica
                if (!memberName.equals(creatorUsername)) {
                     memberHandler.sendMessage(new Message("Servidor", groupNameWithIcon, addedMsgContent, MessageType.GROUP_SYSTEM_MESSAGE));
                }
            }
        }
        broadcastUserList(); // Atualiza as listas de todos
    }

    public synchronized void handleLeaveGroup(String groupNameWithIcon, String usernameLeaving) {
        if (!running) return;
        List<String> members = groups.get(groupNameWithIcon);
        ClientHandler userLeavingHandler = clients.get(usernameLeaving);
        String cleanGroupName = groupNameWithIcon.replace(GROUP_ICON_PREFIX, "").trim();

        if (members != null && userLeavingHandler != null) {
            boolean removed = members.remove(usernameLeaving);
            if (removed) {
                log("INFO", "GRUPO_SAIDA_MEMBRO", usernameLeaving + " saiu do grupo " + groupNameWithIcon);
                // Notifica o usuário que ele saiu
                userLeavingHandler.sendMessage(new Message("Servidor", groupNameWithIcon, "Você saiu do grupo '" + cleanGroupName + "'.", MessageType.GROUP_SYSTEM_MESSAGE));
                userLeavingHandler.sendMessage(new Message("Servidor", usernameLeaving, groupNameWithIcon, MessageType.GROUP_REMOVED_NOTIFICATION)); // Para GUI remover o chat


                if (members.isEmpty()) {
                    groups.remove(groupNameWithIcon);
                    log("INFO", "GRUPO_AUTO_DELETE_VAZIO", "Grupo " + groupNameWithIcon + " ficou vazio e foi removido do servidor.");
                    // O broadcastUserList vai cuidar de remover o grupo das listas dos outros.
                } else {
                    log("INFO", "GRUPO_MEMBROS_RESTANTES", "Grupo " + groupNameWithIcon + " agora tem " + members.size() + " membros: " + members);
                    // Notifica os membros restantes
                    String systemMessageContent = usernameLeaving + " saiu do grupo '" + cleanGroupName + "'.";
                    Message systemMessage = new Message("Servidor", groupNameWithIcon, systemMessageContent, MessageType.GROUP_SYSTEM_MESSAGE);
                    for (String member : members) {
                        ClientHandler memberHandler = clients.get(member);
                        if (memberHandler != null) {
                            memberHandler.sendMessage(systemMessage);
                        }
                    }
                }
                broadcastUserList(); // Atualiza as listas de todos
            } else { // Não era membro, mas tentou sair
                log("AVISO", "GRUPO_SAIDA_FALHA_NAOMEMBRO", usernameLeaving + " tentou sair do grupo " + groupNameWithIcon + " mas não era membro.");
                userLeavingHandler.sendMessage(new Message("Servidor", usernameLeaving, groupNameWithIcon, MessageType.GROUP_REMOVED_NOTIFICATION)); // Para GUI se comportar como se tivesse saído
            }
        } else {
            if (members == null && userLeavingHandler != null) {
                log("AVISO", "GRUPO_SAIDA_FALHA_NAOEXISTE", "Tentativa de sair do grupo " + groupNameWithIcon + " que não existe (notificando cliente).");
                userLeavingHandler.sendMessage(new Message("Servidor", usernameLeaving, groupNameWithIcon, MessageType.GROUP_REMOVED_NOTIFICATION));
            }
            if (userLeavingHandler == null) {
                log("AVISO", "GRUPO_SAIDA_FALHA_USERNF", "Usuário " + usernameLeaving + " não encontrado ao tentar sair do grupo.");
            }
        }
    }

    public synchronized void handleGroupInfoRequest(String groupNameWithIcon, String requestingUsername) {
        if (!running) return;
        ClientHandler requesterHandler = clients.get(requestingUsername);
        if (requesterHandler == null) {
            log("AVISO", "GRUPO_INFO_REQ_USER_NF", "Usuário solicitante " + requestingUsername + " não encontrado.");
            return;
        }

        List<String> members = groups.get(groupNameWithIcon);
        if (members == null) {
            log("AVISO", "GRUPO_INFO_REQ_GRP_NF", "Grupo " + groupNameWithIcon + " não encontrado para solicitação de info por " + requestingUsername);
            requesterHandler.sendMessage(new Message("Servidor", requestingUsername, "Erro: Grupo não encontrado.", MessageType.TEXT));
            return;
        }

        if (!members.contains(requestingUsername)) {
            log("AVISO", "GRUPO_INFO_REQ_NOT_MEMBER", requestingUsername + " solicitou info do grupo " + groupNameWithIcon + " mas não é membro.");
            requesterHandler.sendMessage(new Message("Servidor", requestingUsername, "Erro: Você não é membro deste grupo.", MessageType.TEXT));
            return;
        }

        String membersString = String.join(",", members);
        Message infoResponse = new Message("Servidor", groupNameWithIcon, membersString, MessageType.GROUP_INFO_RESPONSE);
        infoResponse.setReceiver(requestingUsername); 
                                                     
        requesterHandler.sendMessage(infoResponse);
        log("INFO", "GRUPO_INFO_REQ_SENT", "Informações do grupo " + groupNameWithIcon + " enviadas para " + requestingUsername);
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
        String shortStackTrace = "";

        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String[] stackLines = sw.toString().split("\n");
            shortStackTrace = stackLines[0] + (stackLines.length > 1 ? " (" + stackLines[1].trim() + ")" : "");
            exceptionDetails = String.format(" | Exceção: %s - %s (%s)", e.getClass().getSimpleName(), e.getMessage(), shortStackTrace);
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