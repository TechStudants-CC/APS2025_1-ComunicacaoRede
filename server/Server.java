package server;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import common.Message;
import common.MessageType;

public class Server extends JFrame {
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ObjectOutputStream> clients = new ConcurrentHashMap<>();

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
            log("Servidor iniciado na porta 54321");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this).start();
            }
        } catch (IOException e) {
            log("Erro: " + e.getMessage());
        }
    }

    public synchronized void addClient(String username, ObjectOutputStream out) {
        clients.put(username, out);
        updateUserList();
        log(username + " conectou.");
    }

    public synchronized void removeClient(String username) {
        clients.remove(username);
        updateUserList();
        log(username + " desconectou.");
    }

    public synchronized void broadcast(Message msg, String sender) {
        clients.forEach((user, out) -> {
            if (!user.equals(sender)) {
                try {
                    out.writeObject(msg);
                } catch (IOException e) {
                    log("Erro ao enviar para " + user);
                }
            }
        });
    }

    public synchronized void sendPrivate(String receiver, Message msg) {
        ObjectOutputStream out = clients.get(receiver);
        if (out != null) {
            try {
                out.writeObject(msg);
            } catch (IOException e) {
                log("Erro ao enviar para " + receiver);
            }
        }
    }

    private void updateUserList() {
        StringBuilder users = new StringBuilder();
        clients.keySet().forEach(user -> users.append(user).append(","));
        broadcast(new Message("Servidor", null, users.toString(), MessageType.USER_LIST), "Servidor");
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append("[LOG] " + message + "\n"));
    }

    public static void main(String[] args) {
        new Server();
    }
}