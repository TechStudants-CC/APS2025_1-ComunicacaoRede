// common/MessageStatus.java
package common;

/**
 * Enum para representar os diferentes estados de entrega de uma mensagem
 */
public enum MessageStatus {
    SENDING,     // Mensagem sendo enviada
    SENT,        // Mensagem enviada ao servidor
    DELIVERED,   // Mensagem entregue ao destinatário
    READ,        // Mensagem lida pelo destinatário
    FAILED       // Falha no envio da mensagem
}