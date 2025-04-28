package server;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import common.Message;

public class Server extends JFrame {
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ObjectOutputStream> clients = new ConcurrentHashMap<>();

    public Server() {
        setTitle("Servidor - Secretaria do Meio Ambiente");
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
            log("Erro no servidor: " + e.getMessage());
        }
    }

    public synchronized void addClient(String username, ObjectOutputStream out) {
        clients.put(username, out);
        updateUserList();
    }

    public synchronized void removeClient(String username) {
        clients.remove(username);
        updateUserList();
    }

    public synchronized void broadcast(Message msg) {
        for (ObjectOutputStream out : clients.values()) {
            try {
                out.writeObject(msg);
            } catch (IOException e) {
                log("Erro ao enviar mensagem para todos.");
            }
        }
    }

    public synchronized void sendPrivate(String receiver, Message msg) {
        ObjectOutputStream out = clients.get(receiver);
        if (out != null) {
            try {
                out.writeObject(msg);
            } catch (IOException e) {
                log("Erro ao enviar mensagem privada para: " + receiver);
            }
        }
    }

    private synchronized void updateUserList() {
        StringBuilder users = new StringBuilder();
        for (String user : clients.keySet()) {
            users.append(user).append(",");
        }
        Message userList = new Message("Servidor", null, users.toString(), Message.MessageType.USER_LIST);
        broadcast(userList);
    }

    public synchronized void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[LOG] " + message + "\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server());
    }
}
