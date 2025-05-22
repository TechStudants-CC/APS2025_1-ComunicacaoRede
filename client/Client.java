package client;

import common.Message;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException; // Import necessário
import javax.swing.SwingUtilities;

public class Client {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final ClientGUI gui; // gui é final, sua referência não muda
    private Thread listenerThread; // Referência para a thread de escuta

    /**
     * Construtor do Cliente. Estabelece a conexão e prepara os streams.
     * A thread de escuta deve ser iniciada chamando startListening().
     * @param serverIP IP do servidor.
     * @param port Porta do servidor.
     * @param usernameDoGui Nome do usuário obtido da GUI (para envio inicial).
     * @param gui Referência à interface gráfica.
     * @throws IOException Se ocorrer um erro ao conectar ou criar os streams.
     */
    public Client(String serverIP, int port, String usernameDoGui, ClientGUI gui) throws IOException {
        this.gui = gui; // Deve ser o primeiro para que showError possa ser usado se algo falhar abaixo
        try {
            this.socket = new Socket(serverIP, port);
            this.out = new ObjectOutputStream(socket.getOutputStream());
            // É importante dar flush no ObjectOutputStream após criá-lo e antes de criar o ObjectInputStream
            // para garantir que o cabeçalho do stream seja enviado, evitando deadlocks na inicialização.
            this.out.flush(); 
            this.in = new ObjectInputStream(socket.getInputStream());
            
            // Envia o username (recebido como parâmetro, que veio da GUI) para o servidor
            this.out.writeObject(usernameDoGui);
            this.out.flush(); // Garante o envio imediato do username
        } catch (IOException e) {
            // Tenta fechar recursos se a conexão falhar parcialmente
            closeResourcesOnError();
            throw e; // Relança a exceção para que a GUI possa tratá-la
        }
    }

    /**
     * Inicia a thread de escuta de mensagens do servidor.
     * Deve ser chamado após a instância do Client ser completamente criada.
     */
    public void startListening() {
        if (listenerThread == null || !listenerThread.isAlive()) {
            listenerThread = new Thread(this::listen);
            // Usa o getter para o nome da thread para maior segurança e encapsulamento
            String threadName = "ClientListenerThread-";
            if (gui != null && gui.getName() != null) { // Verifica se gui e username são válidos
                threadName += gui.getName();
            } else {
                threadName += "unknownUser";
            }
            listenerThread.setName(threadName);
            listenerThread.start();
        }
    }

    /**
     * Envia uma mensagem para o servidor.
     * @param msg A mensagem a ser enviada.
     */
    public void sendMessage(Message msg) {
        try {
            if (out != null && socket != null && socket.isConnected() && !socket.isOutputShutdown()) {
                out.writeObject(msg);
                out.flush();
            } else {
                String errorMessage = "Não é possível enviar mensagem: ";
                if (out == null) errorMessage += "Stream de saída nulo. ";
                if (socket == null || !socket.isConnected()) errorMessage += "Socket não conectado. ";
                if (socket != null && socket.isOutputShutdown()) errorMessage += "Saída do socket fechada.";
                handleSendError(errorMessage);
            }
        } catch (IOException e) {
            handleSendError("Erro de I/O ao enviar mensagem: " + e.getMessage());
        }
    }
    
    private void handleSendError(String errorMessage) {
        String clientUsername = (gui != null && gui.getName() != null) ? gui.getName() : "desconhecido";
        System.err.println(errorMessage + " (Cliente: " + clientUsername + ")");
        if (gui != null) {
            // Garante que showError seja chamado na EDT
            SwingUtilities.invokeLater(() -> gui.showError(errorMessage));
        }
    }

    /**
     * Loop principal que escuta por mensagens incoming do servidor.
     */
    private void listen() {
        String clientUsername = (gui != null && gui.getName() != null) ? gui.getName() : "desconhecido";
        System.out.println("Thread de escuta do cliente iniciada para: " + clientUsername);
        try {
            Message msg;
            // Continua enquanto o socket estiver conectado, não fechado, e a thread não for interrompida
            while (socket.isConnected() && !socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                msg = (Message) in.readObject(); // Ponto de bloqueio
                if (msg != null) {
                    if (gui != null) {
                        final Message messageToHandle = msg; // Variável final para uso na lambda
                        SwingUtilities.invokeLater(() -> gui.handleMessage(messageToHandle));
                    }
                } else {
                    // O stream retornou null, o que é incomum para readObject a menos que o stream esteja comprometido.
                    System.err.println("Recebido null do ObjectInputStream para " + clientUsername + ", encerrando listen().");
                    break;
                }
            }
        } catch (EOFException e) {
            handleConnectionLoss("Conexão perdida com o servidor (EOF).", clientUsername);
        } catch (SocketException e) {
            // Não mostrar erro se o socket já estiver fechado (pode ter sido por stopClient)
            if (socket != null && !socket.isClosed()) {
                handleConnectionLoss("Erro de socket na escuta: " + e.getMessage(), clientUsername);
            } else {
                 System.out.println("SocketException na thread de escuta (socket já fechado) para " + clientUsername + ": " + e.getMessage());
            }
        } catch (IOException e) {
            if (socket != null && !socket.isClosed()) {
                handleConnectionLoss("Erro de I/O na escuta: " + e.getMessage(), clientUsername);
            } else {
                 System.err.println("IOException na thread de escuta (socket já fechado) para " + clientUsername + ": " + e.getMessage());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Erro de desserialização (ClassNotFoundException) para " + clientUsername + ": " + e.getMessage());
            if (gui != null && (socket == null || !socket.isClosed())) {
                final String errorMsg = "Erro de dados recebidos do servidor: Formato de mensagem desconhecido.";
                SwingUtilities.invokeLater(() -> gui.showError(errorMsg));
            }
        } finally {
            System.out.println("Saindo do loop de escuta para: " + clientUsername);
            // Não chame closeResources() aqui automaticamente, pois pode ser um fechamento parcial.
            // O fechamento principal de recursos deve ser gerenciado por stopClient() ou quando a GUI fecha.
            // Se a thread termina devido a um erro grave, a GUI deve ser notificada para, possivelmente, chamar stopClient().
            System.out.println("Thread de escuta do cliente finalizada para: " + clientUsername);
        }
    }
    
    private void handleConnectionLoss(String logMessage, String clientUsername) {
        System.out.println(logMessage + " (Cliente: " + clientUsername + ")");
        if (gui != null && (socket == null || !socket.isClosed())) {
            // Evita mostrar múltiplos erros se o socket já foi tratado como fechado.
            SwingUtilities.invokeLater(() -> gui.showError("Conexão com o servidor foi perdida."));
        }
    }

    /**
     * Fecha os streams e o socket. Chamado para encerrar o cliente de forma limpa.
     * Este método também tenta interromper a thread de escuta.
     */
    public void stopClient() {
        String clientUsername = (gui != null && gui.getName() != null) ? gui.getName() : "desconhecido";
        System.out.println("Parando cliente: " + clientUsername);
        
        // Primeiro, interrompe a thread para que ela possa sair do bloqueio em readObject()
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
        }
        
        // Em seguida, fecha os recursos de rede
        closeResources(); 
        
        // Espera pela finalização da thread de escuta
        if (listenerThread != null && listenerThread.isAlive()) {
            try {
                listenerThread.join(1000); // Espera no máximo 1 segundo
                if (listenerThread.isAlive()) {
                    System.err.println("Thread de escuta não terminou após o join para " + clientUsername + ".");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
                System.err.println("Thread principal interrompida durante o join da thread de escuta para " + clientUsername + ".");
            }
        }
        System.out.println("Cliente parado: " + clientUsername);
    }

    /**
     * Método auxiliar para fechar recursos de forma segura.
     */
    private void closeResources() {
        String clientUsername = (gui != null && gui.getName() != null) ? gui.getName() : "desconhecido";
        // A ordem de fechamento pode ser importante: output, input, depois socket.
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar ObjectOutputStream para " + clientUsername + ": " + e.getMessage());
        }
        try {
            if (in != null) in.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar ObjectInputStream para " + clientUsername + ": " + e.getMessage());
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar socket do cliente para " + clientUsername + ": " + e.getMessage());
        }
    }

    /**
     * Usado para tentar fechar recursos se o construtor falhar no meio.
     */
    private void closeResourcesOnError() {
        // Ordem reversa da abertura, ou a que fizer mais sentido para evitar bloqueios
        try { if (out != null) out.close(); } catch (IOException e) { /* ignora */ }
        try { if (in != null) in.close(); } catch (IOException e) { /* ignora */ }
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) { /* ignora */ }
    }
}