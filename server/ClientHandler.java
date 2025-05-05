package server;

import java.io.*;
import java.net.*;
import common.Message;
import java.util.Arrays;

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
            
            // Registrar usuário
            username = (String) in.readObject();
            server.addClient(username, out);

            while (true) {
                Message msg = (Message) in.readObject();
                switch (msg.getType()) {
                    case TEXT:
                        server.broadcast(msg, username);
                        break;
                        
                    case PRIVATE:
                        server.sendPrivate(msg.getReceiver(), msg);
                        break;
                        
                    case GROUP:
                        Arrays.stream(msg.getReceiver().split(","))
                            .filter(user -> !user.equals(username))
                            .forEach(user -> server.sendPrivate(user, msg));
                        break;
                        
                    case FILE:
                        server.sendPrivate(msg.getReceiver(), msg);
                        break;
                        
                    case USER_LIST:
                        break; // Não requer ação
                        
                    default:
                        server.log("Tipo de mensagem não reconhecido: " + msg.getType());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            server.removeClient(username);
        } finally {
            try {
                in.close();
                out.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}