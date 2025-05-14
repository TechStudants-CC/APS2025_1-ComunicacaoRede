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
    // private String clientIp; // clientIp pode ser obtido do socket.getInetAddress().getHostAddress()

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        // this.clientIp = socket.getInetAddress().getHostAddress(); // Movido para getRemoteSocketAddress ou usado diretamente
    }

    public ObjectOutputStream getOutputStream() {
        return out;
    }
    public String getUsername() {
        return this.username;
    }

    public SocketAddress getRemoteSocketAddress() {
        return socket.getRemoteSocketAddress();
    }


    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            this.username = (String) in.readObject(); // Lê o nome de usuário enviado pelo cliente
            server.log("INFO", "AUTENTICAÇÃO", "Usuário '" + username + "' conectando de " + socket.getInetAddress().getHostAddress());
            
            // Verifica se o nome de usuário já está em uso
            if (!server.addClient(username, this)) {
                // Nome de usuário já em uso, notificar cliente e fechar conexão
                Message errorMsg = new Message("Servidor", username, "Erro: Nome de usuário já está em uso.", MessageType.TEXT);
                errorMsg.setStatus(MessageStatus.FAILED); // Indica uma falha
                sendMessage(errorMsg);
                server.log("AVISO", "AUTENTICAÇÃO", "Nome de usuário '" + username + "' já em uso. Conexão rejeitada.");
                return; // Encerra a thread do handler
            }


            // Envia a lista inicial de usuários e grupos para o novo cliente
            Message firstUserList = new Message("Servidor", username, server.getUserListString(), MessageType.USER_LIST);
            sendMessage(firstUserList);


            while (socket.isConnected() && !socket.isClosed()) { // Loop enquanto a conexão estiver ativa
                Message msg = (Message) in.readObject();
                if (msg.getTimestamp() == null) { 
                    msg.setTimestamp(new Date());
                }
                processMessage(msg);
            }
        } catch (EOFException e) {
            server.log("INFO", "CONEXÃO", "Cliente " + (username != null ? username : getRemoteSocketAddress()) + " desconectou (EOF).");
        } catch (SocketException e){
            server.log("INFO", "CONEXÃO", "Conexão com " + (username != null ? username : getRemoteSocketAddress()) + " resetada/fechada: " + e.getMessage());
        }
        catch (IOException e) {
            if ("Connection reset".equalsIgnoreCase(e.getMessage()) || "Socket closed".equalsIgnoreCase(e.getMessage())) {
                 server.log("INFO", "CONEXÃO", "Conexão com " + (username != null ? username : getRemoteSocketAddress()) + " foi fechada/resetada.");
            } else {
                server.logError("CONEXÃO_IO", "Erro de I/O com " + (username != null ? username : getRemoteSocketAddress()), e);
            }
        } catch (ClassNotFoundException e) {
            server.logError("PROTOCOLO_HANDLER", "Erro de classe não encontrada de " + (username != null ? username : getRemoteSocketAddress()), e);
        } finally {
            if (username != null) {
                server.removeClient(username);
            }
            closeResources();
        }
    }

    private void processMessage(Message msg) {
        // Log da mensagem recebida para debug
        // server.log("DEBUG", "MSG_RECEBIDA_CH", username + " enviou: " + msg.toString());
        try {
            switch (msg.getType()) {
                case PRIVATE:
                    // Checar se a mensagem tem dados de arquivo
                    if (msg.getFileData() != null && msg.getFileName() != null) {
                        server.log("INFO", "ARQUIVO_PRIVADO", String.format("De: %s | Para: %s | Arquivo: %s (%d bytes)", username, msg.getReceiver(), msg.getFileName(), msg.getFileData().length));
                    } else {
                        server.log("INFO", "MSG_PRIVADA", String.format("De: %s | Para: %s | Conteúdo: %s", username, msg.getReceiver(), server.trimContent(msg.getContent())));
                    }
                    server.sendPrivateMessage(msg, username);
                    break;

                case GROUP:
                     if (msg.getFileData() != null && msg.getFileName() != null) {
                        server.log("INFO", "ARQUIVO_GRUPO", String.format("De: %s | Para Grupo: %s | Arquivo: %s (%d bytes)", username, msg.getReceiver(), msg.getFileName(), msg.getFileData().length));
                    } else {
                        server.log("INFO", "MSG_GRUPO", String.format("De: %s | Para Grupo: %s | Conteúdo: %s", username, msg.getReceiver(), server.trimContent(msg.getContent())));
                    }
                    server.sendGroupMessage(msg, username);
                    break;
                
                case GROUP_CREATE:
                    // Conteúdo esperado: "nomeDoGrupo;membro1,membro2,..."
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
                    // O 'messageId' na mensagem MESSAGE_READ é o ID da mensagem original.
                    // O 'sender' da MESSAGE_READ é quem leu.
                    // O 'receiver' da MESSAGE_READ é o remetente original da mensagem que foi lida.
                    String messageIdRead = msg.getMessageId(); 
                    String originalSenderOfInitialMsg = msg.getReceiver(); 
                    String readerUsername = msg.getSender();

                    server.notifyMessageStatus(
                        originalSenderOfInitialMsg, 
                        messageIdRead,
                        MessageStatus.READ,
                        readerUsername, // Quem leu a mensagem (para o remetente original saber)
                        new Date()
                    );
                    server.log("INFO", "STATUS_LIDO", "Msg " + messageIdRead + " lida por " + readerUsername + ". Notificando remetente original " + originalSenderOfInitialMsg);
                    break;
                
                default:
                    server.log("AVISO", "TIPO_MSG_DESCONHECIDO", "Tipo de mensagem não reconhecido de " + username + ": " + msg.getType());
            }
        } catch (Exception e) {
            server.logError("PROCESSAMENTO_MSG", "Erro ao processar mensagem de " + username + ": " + msg.getContent(), e);
        }
    }
    
    public void sendMessage(Message msg) {
        try {
            if (out != null && socket.isConnected() && !socket.isOutputShutdown()) {
                out.writeObject(msg);
                out.flush(); 
                // server.log("DEBUG", "MSG_ENVIADA_AO_CLIENTE", "Para " + username + ": " + msg.toString());
            } else {
                 server.log("AVISO", "ENVIO_FALHOU_STREAM", "Não foi possível enviar mensagem para " + username + " (stream fechado/inválido).");
            }
        } catch (IOException e) {
            server.logError("ENVIO_CLIENTE_IO", "Erro de I/O ao enviar mensagem para " + username, e);
            // Considerar remover o cliente se o stream estiver consistentemente quebrado
            // server.removeClient(username); 
        }
    }

    private void closeResources() {
        try {
            if (in != null) in.close();
        } catch (IOException e) {
            server.logError("FECHAR_RECURSO_IN", "Erro ao fechar ObjectInputStream para " + (username != null ? username : getRemoteSocketAddress()), e);
        }
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            server.logError("FECHAR_RECURSO_OUT", "Erro ao fechar ObjectOutputStream para " + (username != null ? username : getRemoteSocketAddress()), e);
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            server.logError("FECHAR_RECURSO_SOCKET", "Erro ao fechar socket para " + (username != null ? username : getRemoteSocketAddress()), e);
        }
        server.log("INFO", "RECURSOS_LIBERADOS", "Recursos do cliente " + (username != null ? username : "desconhecido") + " liberados.");
    }
}