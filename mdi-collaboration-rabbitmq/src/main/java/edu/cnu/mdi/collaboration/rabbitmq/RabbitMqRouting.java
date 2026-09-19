package edu.cnu.mdi.collaboration.rabbitmq;

import java.util.UUID;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** Defines the stable RabbitMQ routing-key convention. */
public final class RabbitMqRouting {
    private RabbitMqRouting() {}

    /** Returns a direct-user binding or publishing key. */
    public static String user(UUID id) { return "user." + id; }
    /** Returns a project binding or publishing key. */
    public static String project(UUID id) { return "project." + id; }
    /** Returns the shared online broadcast key. */
    public static String broadcast() { return "broadcast"; }

    /** Chooses the most specific routing key represented by a message. */
    public static String forMessage(CollaborationMessage message) {
        if (message.recipientId() != null) return user(message.recipientId());
        if (message.projectId() != null) return project(message.projectId());
        return broadcast();
    }
}
