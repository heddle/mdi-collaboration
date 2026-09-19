package edu.cnu.mdi.collaboration.rabbitmq;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RabbitMqConfigurationTest {
    @Test void validatesSchemesAndTlsRequirement() {
        assertThrows(IllegalArgumentException.class, () -> configuration("http://localhost", false));
        assertThrows(IllegalArgumentException.class, () -> configuration("amqp://localhost", true));
        assertDoesNotThrow(() -> configuration("amqps://localhost", true));
    }

    @Test void diagnosticsNeverRevealCredentials() {
        RabbitMqConfiguration config = new RabbitMqConfiguration(
                URI.create("amqps://secret-user:secret-password@broker.example:5671/vhost"),
                "override-user", "override-password".toCharArray(), "mdi.test", true, true, Set.of());
        assertEquals("amqps://broker.example:5671", config.safeBrokerAddress());
        assertFalse(config.toString().contains("secret"));
        assertFalse(config.toString().contains("override"));
    }

    @Test void passwordIsDefensivelyCopied() {
        char[] password = "secret".toCharArray();
        RabbitMqConfiguration config = new RabbitMqConfiguration(URI.create("amqp://localhost"),
                "user", password, "mdi.test", false, true, Set.of());
        password[0] = 'X';
        char[] returned = config.password();
        assertEquals('s', returned[0]);
        returned[0] = 'Y';
        assertEquals('s', config.password()[0]);
    }

    private static RabbitMqConfiguration configuration(String uri, boolean tlsRequired) {
        return new RabbitMqConfiguration(URI.create(uri), null, null, "mdi.test",
                tlsRequired, true, Set.of());
    }
}
