package server;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import common.Message;
import common.MessageType;

public class Server extends JFrame {
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ObjectOutputStream> clients = new ConcurrentHashMap<>();
    private AtomicInteger messageIdCounter = new AtomicInteger(1);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public Server() {
        setTitle("Servidor - Logs");
        setSize(600, 400);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        startServer();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(54321);
            log("INFO", "SISTEMA", "Servidor iniciado na porta 54321");
            while (true) {
                Socket socket = serverSocket.accept();
                InetAddress clientAddress = socket.getInetAddress();
                log("INFO", "CONEXÃO", "Nova conexão recebida de " + clientAddress.getHostAddress());
                new ClientHandler(socket, this).start();
            }
        } catch (IOException e) {
            logError("SISTEMA", "Erro ao iniciar servidor", e);
        }
    }

    public synchronized void addClient(String username, ObjectOutputStream out) {
        clients.put(username, out);
        updateUserList();
        log("INFO", "USUÁRIO", "Usuário conectado: " + username);
    }

    public synchronized void removeClient(String username) {
        clients.remove(username);
        updateUserList();
        log("INFO", "USUÁRIO", "Usuário desconectado: " + username);
    }

    public synchronized void broadcast(Message msg, String sender) {
        int msgId = messageIdCounter.getAndIncrement();
        
        log("INFO", "MENSAGEM", String.format(
            "ID:%d | BROADCAST | De:%s | Tipo:%s | Conteúdo:%s",
            msgId, sender, msg.getType(), trimContent(msg.getContent())
        ));
        
        clients.forEach((user, out) -> {
            if (!user.equals(sender)) {
                try {
                    out.writeObject(msg);
                    log("INFO", "ENTREGA", String.format(
                        "ID:%d | ENVIADO | Para:%s | Status:ENVIADO", 
                        msgId, user
                    ));
                } catch (IOException e) {
                    logError("ENTREGA", String.format(
                        "ID:%d | FALHA | Para:%s | Status:ERRO", 
                        msgId, user
                    ), e);
                }
            }
        });
    }

    public synchronized void sendPrivate(String receiver, Message msg) {
        int msgId = messageIdCounter.getAndIncrement();
        ObjectOutputStream out = clients.get(receiver);
        
        log("INFO", "MENSAGEM", String.format(
            "ID:%d | PRIVADA | De:%s | Para:%s | Tipo:%s | Conteúdo:%s",
            msgId, msg.getSender(), receiver, msg.getType(), trimContent(msg.getContent())
        ));
        
        if (out != null) {
            try {
                out.writeObject(msg);
                log("INFO", "ENTREGA", String.format(
                    "ID:%d | ENVIADO | De:%s | Para:%s | Status:ENTREGUE", 
                    msgId, msg.getSender(), receiver
                ));
            } catch (IOException e) {
                logError("ENTREGA", String.format(
                    "ID:%d | FALHA | De:%s | Para:%s | Status:ERRO", 
                    msgId, msg.getSender(), receiver
                ), e);
            }
        } else {
            log("AVISO", "ENTREGA", String.format(
                "ID:%d | FALHA | De:%s | Para:%s | Status:USUÁRIO_DESCONECTADO", 
                msgId, msg.getSender(), receiver
            ));
        }
    }

    private void updateUserList() {
        StringBuilder users = new StringBuilder();
        clients.keySet().forEach(user -> users.append(user).append(","));
        
        Message userListMsg = new Message("Servidor", null, users.toString(), MessageType.USER_LIST);
        int msgId = messageIdCounter.getAndIncrement();
        
        log("INFO", "SISTEMA", String.format(
            "ID:%d | ATUALIZAÇÃO | Tipo:LISTA_USUÁRIOS | Usuários:%s",
            msgId, clients.keySet().toString()
        ));
        
        broadcast(userListMsg, "Servidor");
    }

    public void log(String level, String category, String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] [%s] [%s] %s", timestamp, level, category, message);
        SwingUtilities.invokeLater(() -> logArea.append(logMessage + "\n"));
    }
    
    public void logError(String category, String message, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format(
            "[%s] [ERRO] [%s] %s\nStackTrace:\n%s", 
            timestamp, category, message, sw.toString()
        );
        
        SwingUtilities.invokeLater(() -> logArea.append(logMessage + "\n"));
    }
    
    // Limita o tamanho do conteúdo nos logs para evitar mensagens muito longas
    private String trimContent(String content) {
        if (content == null) return "null";
        if (content.length() <= 50) return content;
        return content.substring(0, 47) + "...";
    }

    public static void main(String[] args) {
        new Server();
    }
}