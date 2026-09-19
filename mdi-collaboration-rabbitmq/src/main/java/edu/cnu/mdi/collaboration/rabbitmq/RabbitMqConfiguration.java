package edu.cnu.mdi.collaboration.rabbitmq;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable RabbitMQ connection and subscription settings. */
public final class RabbitMqConfiguration {
    /** Environment variable containing the AMQP or AMQPS broker URI. */
    public static final String URI_ENV = "MDI_RABBITMQ_URI";
    /** Optional environment variable overriding the URI username. */
    public static final String USERNAME_ENV = "MDI_RABBITMQ_USERNAME";
    /** Optional environment variable overriding the URI password. */
    public static final String PASSWORD_ENV = "MDI_RABBITMQ_PASSWORD";
    /** Optional environment variable overriding the exchange name. */
    public static final String EXCHANGE_ENV = "MDI_RABBITMQ_EXCHANGE";

    private final URI brokerUri;
    private final String username;
    private final char[] password;
    private final String exchangeName;
    private final boolean tlsRequired;
    private final boolean automaticRecovery;
    private final Set<UUID> projectIds;

    /** Creates validated RabbitMQ settings. */
    public RabbitMqConfiguration(URI brokerUri, String username, char[] password,
            String exchangeName, boolean tlsRequired, boolean automaticRecovery,
            Set<UUID> projectIds) {
        this.brokerUri = validateUri(brokerUri, tlsRequired);
        this.username = blankToNull(username);
        this.password = password == null ? null : password.clone();
        this.exchangeName = requireToken(exchangeName, "exchangeName");
        this.tlsRequired = tlsRequired;
        this.automaticRecovery = automaticRecovery;
        this.projectIds = Set.copyOf(Objects.requireNonNullElse(projectIds, Set.of()));
        if (this.password != null && this.username == null) {
            throw new IllegalArgumentException("password requires username");
        }
    }

    /** Loads demo-safe settings from environment variables. */
    public static RabbitMqConfiguration fromEnvironment() {
        String uri = System.getenv(URI_ENV);
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(URI_ENV + " must contain an amqp:// or amqps:// broker URI");
        }
        String exchange = System.getenv().getOrDefault(EXCHANGE_ENV, "mdi.collaboration");
        return new RabbitMqConfiguration(URI.create(uri), System.getenv(USERNAME_ENV),
                chars(System.getenv(PASSWORD_ENV)), exchange, false, true, Set.of());
    }

    private static URI validateUri(URI uri, boolean tlsRequired) {
        Objects.requireNonNull(uri, "brokerUri");
        if (!"amqp".equalsIgnoreCase(uri.getScheme()) && !"amqps".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("brokerUri scheme must be amqp or amqps");
        }
        if (tlsRequired && !"amqps".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("TLS-required configuration must use amqps");
        }
        return uri;
    }

    private static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty() || result.length() > 255) throw new IllegalArgumentException(name + " is invalid");
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static char[] chars(String value) { return value == null ? null : value.toCharArray(); }

    /** Returns the broker URI. Callers must not log credentials embedded in it. */
    public URI brokerUri() { return brokerUri; }
    /** Returns the optional username override. */
    public String username() { return username; }
    /** Returns a defensive copy of the optional password override. */
    public char[] password() { return password == null ? null : password.clone(); }
    /** Returns the topic exchange name. */
    public String exchangeName() { return exchangeName; }
    /** Returns whether AMQPS is mandatory. */
    public boolean tlsRequired() { return tlsRequired; }
    /** Returns whether client automatic recovery is enabled. */
    public boolean automaticRecovery() { return automaticRecovery; }
    /** Returns immutable project subscriptions. */
    public Set<UUID> projectIds() { return projectIds; }

    /** Returns a credential-free address suitable for diagnostics. */
    public String safeBrokerAddress() {
        int port = brokerUri.getPort();
        return brokerUri.getScheme() + "://" + Objects.toString(brokerUri.getHost(), "")
                + (port < 0 ? "" : ":" + port);
    }

    /** Never includes credentials. */
    @Override public String toString() {
        return "RabbitMqConfiguration[broker=" + safeBrokerAddress() + ", exchange="
                + exchangeName + ", tlsRequired=" + tlsRequired + "]";
    }
}
