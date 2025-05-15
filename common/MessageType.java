// common/MessageType.java
package common;

public enum MessageType {
    TEXT,
    PRIVATE,
    GROUP,
    FILE,
    USER_LIST,
    GROUP_CREATE,
    STATUS_UPDATE,
    MESSAGE_READ,

    // Novas para gerenciamento de grupos
    LEAVE_GROUP,                // Cliente para Servidor: Usuário quer sair de um grupo
    GROUP_REMOVED_NOTIFICATION  // Servidor para Cliente: Notifica que o usuário foi removido de um grupo (ou o grupo foi excluído)
}