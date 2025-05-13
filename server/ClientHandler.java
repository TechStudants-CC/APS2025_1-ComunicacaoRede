// server/ClientHandler.java
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
    private String clientIp;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Registrar usuário
            username = (String) in.readObject();
            server.log("INFO", "AUTENTICAÇÃO", "Usuário '" + username + "' está se conectando de " + clientIp);
            server.addClient(username, out);

            while (true) {
                Message msg = (Message) in.readObject();
                processMessage(msg);
            }
        } catch (EOFException e) {
            server.log("INFO", "CONEXÃO", "Conexão encerrada com " + username + " (" + clientIp + ")");
            server.removeClient(username);
        } catch (IOException e) {
            server.log("ERRO", "CONEXÃO", "Falha na conexão com " + username + " (" + clientIp + "): " + e.getMessage());
            server.removeClient(username);
        } catch (ClassNotFoundException e) {
            server.logError("PROTOCOLO", "Erro ao processar objeto recebido de " + username, e);
            server.removeClient(username);
        } finally {
            closeResources();
        }
    }
    
    private void processMessage(Message msg) {
        try {
            switch (msg.getType()) {
                case TEXT:
                    server.log("INFO", "RECEBIDO", String.format(
                        "De:%s | Tipo:TEXTO | Conteúdo:%s", 
                        username, msg.getContent().length() > 50 ? msg.getContent().substring(0, 47) + "..." : msg.getContent()
                    ));
                    server.broadcast(msg, username);
                    break;
                    
                case PRIVATE:
                    server.log("INFO", "RECEBIDO", String.format(
                        "De:%s | Para:%s | Tipo:PRIVADA | Tamanho:%d bytes", 
                        username, msg.getReceiver(), msg.getContent().length()
                    ));
                    server.sendPrivate(msg.getReceiver(), msg);
                    break;
                    
                case GROUP:
                    String[] recipients = msg.getReceiver().split(",");
                    server.log("INFO", "RECEBIDO", String.format(
                        "De:%s | Tipo:GRUPO | Destinatários:%d | Conteúdo:%s", 
                        username, recipients.length, 
                        msg.getContent().length() > 50 ? msg.getContent().substring(0, 47) + "..." : msg.getContent()
                    ));
                    
                    Arrays.stream(recipients)
                        .filter(user -> !user.equals(username))
                        .forEach(user -> server.sendPrivate(user, msg));
                    break;
                    
                case FILE:
                    server.log("INFO", "RECEBIDO", String.format(
                        "De:%s | Para:%s | Tipo:ARQUIVO | Nome:%s | Tamanho:%d bytes", 
                        username, msg.getReceiver(), msg.getFileName(), 
                        msg.getFileData() != null ? msg.getFileData().length : 0
                    ));
                    server.sendPrivate(msg.getReceiver(), msg);
                    break;
                    
                case USER_LIST:
                    server.log("INFO", "SISTEMA", "Requisição de lista de usuários de " + username);
                    break;
                    
                case GROUP_CREATE:
                    server.log("INFO", "GRUPO", String.format(
                        "Criação de grupo por %s | Membros: %s", 
                        username, msg.getContent()
                    ));
                    break;
                    
                default:
                    server.log("AVISO", "DESCONHECIDO", "Tipo de mensagem não reconhecido de " + username + ": " + msg.getType());
            }
        } catch (Exception e) {
            server.logError("PROCESSAMENTO", "Erro ao processar mensagem de " + username, e);
        }
    }
    
    private void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            server.log("INFO", "RECURSOS", "Recursos do cliente " + username + " liberados");
        } catch (IOException e) {
            server.logError("RECURSOS", "Erro ao fechar recursos do cliente " + username, e);
        }
    }
}