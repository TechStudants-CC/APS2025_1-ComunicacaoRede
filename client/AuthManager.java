package client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

public class AuthManager {

    private static final String DATABASE_URL = "jdbc:sqlite:client_chat_users.db";
    private static final int SALT_LENGTH = 16; // Comprimento do salt em bytes

    public AuthManager() {
        // Tenta carregar explicitamente a classe do driver SQLite
        // Isso é útil para diagnóstico e para garantir que o driver seja registrado
        // em ambientes onde o carregamento automático de serviço pode falhar.
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SUCESSO: Driver SQLite JDBC (org.sqlite.JDBC) carregado via Class.forName().");
        } catch (ClassNotFoundException e) {
            System.err.println("FALHA CRÍTICA: Driver SQLite JDBC (org.sqlite.JDBC) NÃO encontrado no classpath.");
            System.err.println("Certifique-se de que o arquivo JAR do SQLite JDBC (ex: sqlite-jdbc-VERSION.jar) está na pasta 'lib' e incluído no classpath de execução.");
            // e.printStackTrace(); // Consider logging this exception to a file or logging framework instead.
            // Considerar lançar uma RuntimeException ou tratar de forma que a aplicação não continue
            // se o driver for essencial e não puder ser carregado.
            // throw new RuntimeException("Driver SQLite não encontrado, a aplicação não pode continuar.", e);
        }
        
        initDatabase();
    }

    private Connection connect() throws SQLException {
        // DriverManager tentará encontrar um driver adequado para a URL fornecida.
        // Se Class.forName() funcionou, o driver org.sqlite.JDBC já está registrado.
        return DriverManager.getConnection(DATABASE_URL);
    }

    private void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                   + " username TEXT PRIMARY KEY NOT NULL,"
                   + " password_hash TEXT NOT NULL,"
                   + " salt TEXT NOT NULL"
                   + ");";
        try (Connection conn = connect(); // Esta chamada pode lançar "No suitable driver" se o driver não foi carregado/registrado
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Banco de dados inicializado/verificado com sucesso.");
        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados: " + e.getMessage());
            // Se a mensagem for "No suitable driver found", o problema persiste no DriverManager.
            // Se for outra SQLException, o driver foi encontrado, mas ocorreu outro erro SQL.
            // e.printStackTrace(); // Imprime o stack trace completo para mais detalhes. (Removido para evitar uso de printStackTrace)
        }
    }

    private byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao gerar hash da senha: Algoritmo SHA-256 não encontrado.", e);
        }
    }

    public boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            System.err.println("Nome de usuário e senha não podem ser vazios.");
            return false;
        }
        byte[] salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);
        String saltString = Base64.getEncoder().encodeToString(salt);

        String sql = "INSERT INTO users(username, password_hash, salt) VALUES(?,?,?)";

        try (Connection conn = connect(); // Esta chamada pode lançar "No suitable driver"
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, saltString);
            pstmt.executeUpdate();
            System.out.println("Usuário '" + username + "' registrado com sucesso.");
            return true;
        } catch (SQLException e) {
            if (e.getMessage().startsWith("[SQLITE_CONSTRAINT_PRIMARYKEY]") || (e.getErrorCode() == 19 && e.getMessage().toLowerCase().contains("unique constraint failed: users.username"))) {
                 System.err.println("Falha no registro: Nome de usuário '" + username + "' já existe.");
            } else {
                System.err.println("Erro SQL ao registrar usuário '" + username + "': " + e.getMessage());
                // Detalhes do erro: " + e.toString()
            }
            return false;
        }
    }

    public boolean loginUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        String sql = "SELECT password_hash, salt FROM users WHERE username = ?";

        try (Connection conn = connect(); // Esta chamada pode lançar "No suitable driver"
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String saltString = rs.getString("salt");
                byte[] salt = Base64.getDecoder().decode(saltString);

                String inputHash = hashPassword(password, salt);
                return inputHash.equals(storedHash);
            } else {
                return false; // Usuário não encontrado
            }
        } catch (SQLException e) {
            System.err.println("Erro SQL ao fazer login do usuário '" + username + "': " + e.getMessage());
            // Detalhes do erro: " + e.toString()
            return false;
        }
    }
}
