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
    GROUP_REMOVED_NOTIFICATION, // Servidor para Cliente: Notifica que o usuário foi removido de um grupo (ou o grupo foi excluído)

    // Novas para informações e eventos de grupo
    GROUP_SYSTEM_MESSAGE,       // Servidor para Cliente: Mensagem de sistema sobre um grupo (ex: user left, you were added)
    GROUP_INFO_REQUEST,         // Cliente para Servidor: Solicita informações de um grupo (ex: lista de membros)
    GROUP_INFO_RESPONSE         // Servidor para Cliente: Resposta com informações do grupo
}