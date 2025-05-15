// server/ClientHandler.java
package server;

import java.io.*;
import java.net.*;
import common.Message;
import common.MessageType;
import common.MessageStatus;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ClientHandler extends Thread {
    private Socket socket;
    private Server server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() {
        return this.username;
    }

    public SocketAddress getRemoteSocketAddress() {
        if (socket != null) {
            return socket.getRemoteSocketAddress();
        }
        return null;
    }
    
    public Socket getSocket() {
        return this.socket;
    }

    public void closeClientSocket() {
        this.running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            server.logError("CLOSE_CLIENT_SOCKET", "Erro ao fechar socket para " + (username != null ? username : "desconhecido"), e);
        }
    }


    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            this.username = (String) in.readObject(); 
            server.log("INFO", "AUTENTICAÇÃO", "Usuário '" + username + "' conectando de " + socket.getInetAddress().getHostAddress());
            
            if (!server.addClient(username, this)) {
                Message errorMsg = new Message("Servidor", username, "Erro: Nome de usuário já está em uso.", MessageType.TEXT);
                errorMsg.setStatus(MessageStatus.FAILED);
                sendMessage(errorMsg);
                server.log("AVISO", "AUTENTICAÇÃO_FALHA", "Nome de usuário '" + username + "' já em uso. Conexão com " + getRemoteSocketAddress() + " será fechada.");
                this.running = false;
            } else {
                Message firstUserList = new Message("Servidor", username, server.getUserListString(), MessageType.USER_LIST);
                sendMessage(firstUserList);
            }

            while (running && socket.isConnected() && !socket.isClosed()) {
                Message msg = (Message) in.readObject();
                if (!running) break;

                if (msg.getTimestamp() == null) { 
                    msg.setTimestamp(new Date());
                }
                processMessage(msg);
            }
        } catch (EOFException e) {
            if (running) server.log("INFO", "CONEXÃO_EOF", "Cliente " + (username != null ? username : getRemoteSocketAddress()) + " desconectou (EOF).");
        } catch (SocketException e){
            if (running) server.log("INFO", "CONEXÃO_SOCKET_ERR", "SocketException com " + (username != null ? username : getRemoteSocketAddress()) + ": " + e.getMessage() + (socket.isClosed() ? " (Socket fechado)" : ""));
        }
        catch (IOException e) {
            if (running) {
                if ("Connection reset".equalsIgnoreCase(e.getMessage()) || e.getMessage().toLowerCase().contains("socket closed") || e.getMessage().toLowerCase().contains("stream closed")) {
                     server.log("INFO", "CONEXÃO_IO_RESET", "Conexão com " + (username != null ? username : getRemoteSocketAddress()) + " foi encerrada.");
                } else {
                    server.logError("CONEXÃO_IO_HANDLER", "Erro de I/O com " + (username != null ? username : getRemoteSocketAddress()), e);
                }
            }
        } catch (ClassNotFoundException e) {
            if (running) server.logError("PROTOCOLO_HANDLER_CNFE", "Erro de classe não encontrada de " + (username != null ? username : getRemoteSocketAddress()), e);
        } finally {
            if (username != null) {
                server.removeClient(username);
            }
            closeResourcesFinal();
            if(running) server.log("INFO", "HANDLER_END", "Thread do ClientHandler para " + (username != null ? username : "desconhecido") + " terminada.");
             else server.log("INFO", "HANDLER_SHUTDOWN", "Thread do ClientHandler para " + (username != null ? username : "desconhecido") + " desligada.");
        }
    }

    private void processMessage(Message msg) {
        if (!running) return;
        try {
            switch (msg.getType()) {
                case PRIVATE:
                case GROUP: 
                    server.routeMessage(msg, username);
                    break;
                
                case GROUP_CREATE:
                    String[] parts = msg.getContent().split(";", 2);
                    if (parts.length < 2) {
                        server.log("AVISO", "GRUPO_CRIA_MALFORMADO", "Msg de criação de grupo malformada de " + username);
                        return;
                    }
                    String groupName = parts[0];
                    List<String> members = Arrays.asList(parts[1].split(","));
                    server.createGroup(groupName, members, username);
                    break;

                case MESSAGE_READ:
                    String messageIdRead = msg.getMessageId(); 
                    String originalSenderOfInitialMsg = msg.getReceiver(); 
                    String readerUsername = msg.getSender();
                    server.notifyMessageStatus(originalSenderOfInitialMsg, messageIdRead, MessageStatus.READ, readerUsername, new Date());
                    break;
                
                case LEAVE_GROUP:
                    String groupToLeave = msg.getReceiver(); // O receiver da msg LEAVE_GROUP é o nome do grupo
                    server.handleLeaveGroup(groupToLeave, username); // Passa o nome do usuário que está saindo
                    break;
                
                default:
                    server.log("AVISO", "TIPO_MSG_DESCONHECIDO", "Tipo de mensagem não reconhecido de " + username + ": " + msg.getType());
            }
        } catch (Exception e) {
            server.logError("PROCESSAMENTO_MSG_HANDLER", "Erro ao processar mensagem de " + username + ": " + msg.getContent(), e);
        }
    }
    
    public void sendMessage(Message msg) {
        if (!running || out == null || socket == null || socket.isOutputShutdown() || socket.isClosed()) {
            return;
        }
        try {
            synchronized(out) { 
                out.writeObject(msg);
                out.flush(); 
            }
        } catch (SocketException se) {
            if (running) server.log("AVISO","ENVIO_MSG_SOCKET_EX", "SocketException ao enviar para " + username +": " + se.getMessage());
            this.closeClientSocket(); 
        }
        catch (IOException e) {
            if (running) server.logError("ENVIO_CLIENTE_IO_HANDLER", "Erro de I/O ao enviar mensagem para " + username, e);
            this.closeClientSocket(); 
        }
    }

    private void closeResourcesFinal() { 
        try {
            if (in != null) in.close();
        } catch (IOException e) { /* ignora no shutdown */ }
        try {
            if (out != null) out.close();
        } catch (IOException e) { /* ignora no shutdown */ }
    }
}