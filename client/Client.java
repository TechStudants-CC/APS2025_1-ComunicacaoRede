// client/Client.java
package client;

import java.io.*;
import java.net.*;
import common.Message;

public class Client {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ClientGUI gui;

    public Client(String serverIP, int port, String username, ClientGUI gui) throws IOException {
        this.gui = gui;
        socket = new Socket(serverIP, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        out.writeObject(username);
        new Thread(this::listen).start();
    }

    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
        } catch (IOException e) {
            gui.showError("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    private void listen() {
        try {
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                gui.handleMessage(msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            gui.showError("Conexão perdida: " + e.getMessage());
        }
    }
}