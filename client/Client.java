package client;

import java.io.*;
import java.net.*;
import common.Message;
public class Client {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ClientGUI gui;
    private String username;

    public Client(String serverIP, int port, String username, ClientGUI gui) throws IOException {
        this.username = username;
        this.gui = gui;
        
        // Conecta ao servidor
        socket = new Socket(serverIP, port);
        
        // Inicializa streams
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        // Envia o nome do usuário para o servidor
        out.writeObject(username);

        // Inicia a escuta em nova thread
        new Thread(() -> listen()).start();
    }

    private void listen() {
        try {
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                gui.handleMessage(msg);
                System.out.println("Mensagem recebida pelo usuário: " + username);
            }
        } catch (IOException e) {
            gui.showError("Conexão perdida com o servidor.");
        } catch (ClassNotFoundException e) {
            gui.showError("Erro de leitura de mensagem.");
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão do cliente.");
            }
        }
    }

    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
        } catch (IOException e) {
            gui.showError("Erro ao enviar mensagem.");
        }
    }

    public void close() throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
