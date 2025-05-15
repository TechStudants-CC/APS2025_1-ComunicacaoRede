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
    private volatile boolean running = true; // Para controlar o loop da thread

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    public ObjectOutputStream getOutputStream() {
        return out;
    }
    public String getUsername() {
        return this.username;
    }

    public SocketAddress getRemoteSocketAddress() {
        if (socket != null) {
            return socket.getRemoteSocketAddress();
        }
        return null; // Ou algum endereço placeholder
    }
    
    public Socket getSocket() { // Adicionado para permitir o fechamento pelo Server
        return this.socket;
    }

    public void closeClientSocket() { // Método para ser chamado pelo servidor durante o shutdown
        this.running = false; // Sinaliza para o loop parar
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close(); // Isso deve interromper operações de bloqueio no socket (readObject)
            }
        } catch (IOException e) {
            server.logError("CLOSE_CLIENT_SOCKET", "Erro ao fechar socket para " + (username != null ? username : "desconhecido"), e);
        }
    }


    @Override
    public void run() {
        try {
            // Garante que os streams sejam criados antes de qualquer coisa
            // O ObjectOutputStream deve ser criado ANTES do ObjectInputStream para evitar deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Envia o cabeçalho do stream
            in = new ObjectInputStream(socket.getInputStream());

            this.username = (String) in.readObject(); 
            server.log("INFO", "AUTENTICAÇÃO", "Usuário '" + username + "' conectando de " + socket.getInetAddress().getHostAddress());
            
            if (!server.addClient(username, this)) {
                Message errorMsg = new Message("Servidor", username, "Erro: Nome de usuário já está em uso.", MessageType.TEXT);
                errorMsg.setStatus(MessageStatus.FAILED);
                sendMessage(errorMsg); // Tenta enviar mensagem de erro antes de fechar
                server.log("AVISO", "AUTENTICAÇÃO_FALHA", "Nome de usuário '" + username + "' já em uso. Conexão com " + getRemoteSocketAddress() + " será fechada.");
                this.running = false; // Impede processamento adicional
                // Não remove o cliente aqui, pois ele não foi totalmente adicionado ou addClient falhou.
                // O finally cuidará do closeResources.
            } else {
                 // Envia a lista inicial de usuários e grupos para o novo cliente
                Message firstUserList = new Message("Servidor", username, server.getUserListString(), MessageType.USER_LIST);
                sendMessage(firstUserList);
            }

            while (running && socket.isConnected() && !socket.isClosed()) {
                Message msg = (Message) in.readObject(); // Bloqueia aqui esperando por mensagens
                if (!running) break; // Verifica novamente após o readObject

                if (msg.getTimestamp() == null) { 
                    msg.setTimestamp(new Date());
                }
                processMessage(msg);
            }
        } catch (EOFException e) {
            if (running) server.log("INFO", "CONEXÃO_EOF", "Cliente " + (username != null ? username : getRemoteSocketAddress()) + " desconectou inesperadamente (EOF).");
        } catch (SocketException e){
            if (running) server.log("INFO", "CONEXÃO_SOCKET_ERR", "SocketException com " + (username != null ? username : getRemoteSocketAddress()) + ": " + e.getMessage() + (socket.isClosed() ? " (Socket fechado)" : ""));
        }
        catch (IOException e) {
            if (running) { // Só loga se não for um fechamento esperado
                if ("Connection reset".equalsIgnoreCase(e.getMessage()) || e.getMessage().toLowerCase().contains("socket closed")) {
                     server.log("INFO", "CONEXÃO_IO_RESET", "Conexão com " + (username != null ? username : getRemoteSocketAddress()) + " foi resetada/fechada.");
                } else {
                    server.logError("CONEXÃO_IO_HANDLER", "Erro de I/O com " + (username != null ? username : getRemoteSocketAddress()), e);
                }
            }
        } catch (ClassNotFoundException e) {
            if (running) server.logError("PROTOCOLO_HANDLER_CNFE", "Erro de classe não encontrada de " + (username != null ? username : getRemoteSocketAddress()), e);
        } finally {
            if (username != null) { // Garante que só remove se o username foi estabelecido
                server.removeClient(username);
            }
            closeResources(); // Fecha os streams deste handler
            if(running) server.log("INFO", "HANDLER_END", "Thread do ClientHandler para " + (username != null ? username : "desconhecido") + " terminada.");
        }
    }

    private void processMessage(Message msg) {
        if (!running) return; // Não processa se o handler está parando
        try {
            switch (msg.getType()) {
                case PRIVATE:
                    if (msg.getFileData() != null && msg.getFileName() != null) {
                        server.log("INFO", "ARQUIVO_PRIVADO_RECV", String.format("De: %s | Para: %s | Arquivo: %s", username, msg.getReceiver(), msg.getFileName()));
                    } else {
                        // server.log("INFO", "MSG_PRIVADA_RECV", String.format("De: %s | Para: %s | Conteúdo: %s", username, msg.getReceiver(), server.trimContent(msg.getContent())));
                    }
                    server.sendPrivateMessage(msg, username);
                    break;

                case GROUP:
                     if (msg.getFileData() != null && msg.getFileName() != null) {
                        server.log("INFO", "ARQUIVO_GRUPO_RECV", String.format("De: %s | Para Grupo: %s | Arquivo: %s", username, msg.getReceiver(), msg.getFileName()));
                    } else {
                        // server.log("INFO", "MSG_GRUPO_RECV", String.format("De: %s | Para Grupo: %s | Conteúdo: %s", username, msg.getReceiver(), server.trimContent(msg.getContent())));
                    }
                    server.sendGroupMessage(msg, username);
                    break;
                
                case GROUP_CREATE:
                    String[] parts = msg.getContent().split(";", 2);
                    if (parts.length < 2) {
                        server.log("AVISO", "GRUPO_CRIA_MALFORMADO", "Mensagem de criação de grupo malformada de " + username + ": " + msg.getContent());
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

                    server.notifyMessageStatus(
                        originalSenderOfInitialMsg, 
                        messageIdRead,
                        MessageStatus.READ,
                        readerUsername,
                        new Date()
                    );
                    // server.log("INFO", "STATUS_LIDO_PROC", "Msg " + messageIdRead + " lida por " + readerUsername + ". Notificando remetente original " + originalSenderOfInitialMsg);
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
            // server.log("AVISO", "ENVIO_FALHOU_PRECHECK", "Não foi possível enviar mensagem para " + username + " (handler/socket não pronto ou fechando).");
            return;
        }
        try {
            out.writeObject(msg);
            out.flush(); 
        } catch (SocketException se) {
            if (running) server.log("AVISO","ENVIO_MSG_SOCKET_EX", "SocketException ao enviar para " + username +": " + se.getMessage() + (socket.isClosed() ? " (Socket agora fechado)" : ""));
            this.running = false; // Provavelmente a conexão caiu, sinaliza para parar
        }
        catch (IOException e) {
            if (running) server.logError("ENVIO_CLIENTE_IO_HANDLER", "Erro de I/O ao enviar mensagem para " + username, e);
            this.running = false; // Sinaliza para parar em caso de erro de IO sério
        }
    }

    private void closeResources() { // Chamado no finally do run()
        try {
            if (in != null) in.close();
        } catch (IOException e) {
            // Não logar erro aqui se !running, pois é esperado durante shutdown
            if(running) server.logError("FECHAR_IN_HANDLER", "Erro ao fechar InputStream para " + (username != null ? username : getRemoteSocketAddress()), e);
        }
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            if(running) server.logError("FECHAR_OUT_HANDLER", "Erro ao fechar OutputStream para " + (username != null ? username : getRemoteSocketAddress()), e);
        }
        // O socket principal é fechado pelo método closeClientSocket() ou pelo finally se ocorrer exceção antes
        // Não feche o 'socket' diretamente aqui novamente se closeClientSocket() já foi chamado.
    }
}