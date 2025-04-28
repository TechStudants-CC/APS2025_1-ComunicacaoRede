package server;

import java.io.*;
import java.net.*;
import common.Message;

public class ClientHandler extends Thread {
    private Socket socket;
    private Server server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            username = (String) in.readObject();
            server.addClient(username, out);
            server.log("Usuário conectado: " + username);

            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                switch (msg.getType()) {
                    case TEXT:
                        server.broadcast(msg);
                        server.log("Mensagem pública de " + msg.getSender());
                        break;
                    case PRIVATE:
                        server.sendPrivate(msg.getReceiver(), msg);
                        server.log("Mensagem privada de " + msg.getSender() + " para " + msg.getReceiver());
                        break;
                    case GROUP:
                        handleGroupMessage(msg);
                        server.log("Mensagem de grupo enviada por " + msg.getSender());
                        break;
                    case FILE:
                        server.sendPrivate(msg.getReceiver(), msg);
                        server.log("Arquivo enviado de " + msg.getSender() + " para " + msg.getReceiver() + ": " + msg.getFileName());
                        break;
                    case CONFIRM_READ:
                        server.sendPrivate(msg.getReceiver(), msg);
                        break;
                    default:
                        server.log("Tipo de mensagem não reconhecido: " + msg.getType());
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            server.log("Conexão perdida com: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleGroupMessage(Message msg) {
        if (msg.getReceiver() != null && !msg.getReceiver().isEmpty()) {
            String[] receivers = msg.getReceiver().split(",");
            for (String receiver : receivers) {
                receiver = receiver.trim();
                if (!receiver.equals(msg.getSender())) {
                    server.sendPrivate(receiver, msg);
                }
            }
        }
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            server.log("Erro ao fechar recursos de " + username);
        }
        if (username != null) {
            server.removeClient(username);
            server.log("Usuário desconectado: " + username);
        }
    }
}
