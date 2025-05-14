// common/MessageType.java
package common;

public enum MessageType {
    TEXT,
    PRIVATE,
    GROUP,
    FILE,
    USER_LIST,
    GROUP_CREATE,
    STATUS_UPDATE, // Existente: Para o servidor notificar o cliente sobre mudanças de status
    MESSAGE_READ   // Novo: Para o cliente notificar o servidor que uma mensagem foi lida
}