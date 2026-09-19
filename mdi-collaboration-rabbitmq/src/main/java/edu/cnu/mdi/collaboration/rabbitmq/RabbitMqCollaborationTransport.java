package edu.cnu.mdi.collaboration.rabbitmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;

import edu.cnu.mdi.collaboration.codec.CollaborationMessageCodec;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.transport.CollaborationTransport;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportEvent;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportListener;

/** RabbitMQ topic-exchange implementation of the collaboration transport. */
public final class RabbitMqCollaborationTransport implements CollaborationTransport {
    private final UUID localCollaboratorId;
    private final RabbitMqConfiguration configuration;
    private final CollaborationMessageCodec codec;
    private final CopyOnWriteArrayList<CollaborationTransportListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService executor;
    private final RabbitMqInboundDecoder inboundDecoder;
    private volatile Connection connection;
    private volatile Channel channel;

    /** Creates a transport for one local collaborator. */
    public RabbitMqCollaborationTransport(UUID localCollaboratorId,
            RabbitMqConfiguration configuration) {
        this(localCollaboratorId, configuration, new CollaborationMessageCodec());
    }

    RabbitMqCollaborationTransport(UUID localCollaboratorId,
            RabbitMqConfiguration configuration, CollaborationMessageCodec codec) {
        this.localCollaboratorId = Objects.requireNonNull(localCollaboratorId, "localCollaboratorId");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mdi-collaboration-rabbitmq-" + localCollaboratorId);
            thread.setDaemon(true);
            return thread;
        });
        this.inboundDecoder = new RabbitMqInboundDecoder(codec, this::fire);
    }

    @Override public CompletableFuture<Void> connect() {
        if (closed.get()) return failed(new IllegalStateException("transport is closed"));
        return CompletableFuture.runAsync(() -> {
            if (connected.get()) return;
            try {
                ConnectionFactory factory = createFactory();
                Connection newConnection = factory.newConnection(executor,
                        "mdi-collaboration-" + localCollaboratorId);
                this.connection = newConnection;
                Channel newChannel = newConnection.createChannel();
                this.channel = newChannel;
                declareTopology(newChannel);
                connected.set(true);
                newConnection.addShutdownListener(cause -> {
                    if (!closed.get() && connected.compareAndSet(true, false)) {
                        fire(new CollaborationTransportEvent.Error(cause));
                        fire(new CollaborationTransportEvent.Disconnected());
                    }
                });
                if (newConnection instanceof Recoverable recoverable) {
                    recoverable.addRecoveryListener(new RecoveryListener() {
                        @Override public void handleRecoveryStarted(Recoverable ignored) {
                            if (!closed.get() && connected.compareAndSet(true, false)) {
                                fire(new CollaborationTransportEvent.Disconnected());
                            }
                        }

                        @Override public void handleRecovery(Recoverable ignored) {
                            if (!closed.get() && connected.compareAndSet(false, true)) {
                                fire(new CollaborationTransportEvent.Connected());
                            }
                        }
                    });
                }
                fire(new CollaborationTransportEvent.Connected());
            } catch (Exception error) {
                closeResources();
                connected.set(false);
                fire(new CollaborationTransportEvent.Error(error));
                throw new java.util.concurrent.CompletionException(error);
            }
        }, executor);
    }

    private ConnectionFactory createFactory() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(configuration.brokerUri());
        if (configuration.username() != null) factory.setUsername(configuration.username());
        char[] password = configuration.password();
        if (password != null) factory.setPassword(new String(password));
        factory.setAutomaticRecoveryEnabled(configuration.automaticRecovery());
        factory.setTopologyRecoveryEnabled(configuration.automaticRecovery());
        return factory;
    }

    private void declareTopology(Channel target) throws IOException {
        target.exchangeDeclare(configuration.exchangeName(), "topic", true);
        String queue = target.queueDeclare("", false, true, true, null).getQueue();
        target.queueBind(queue, configuration.exchangeName(), RabbitMqRouting.user(localCollaboratorId));
        target.queueBind(queue, configuration.exchangeName(), RabbitMqRouting.broadcast());
        for (UUID projectId : configuration.projectIds()) {
            target.queueBind(queue, configuration.exchangeName(), RabbitMqRouting.project(projectId));
        }
        target.basicConsume(queue, true,
                (consumerTag, delivery) -> inboundDecoder.accept(delivery.getBody()),
                consumerTag -> { });
    }

    @Override public CompletableFuture<Void> disconnect() {
        if (closed.get() && !connected.get()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            boolean wasConnected = connected.getAndSet(false);
            closeResources();
            if (wasConnected) fire(new CollaborationTransportEvent.Disconnected());
        }, executor);
    }

    @Override public boolean isConnected() {
        Connection current = connection;
        return connected.get() && current != null && current.isOpen();
    }

    @Override public CompletableFuture<Void> publish(CollaborationMessage message) {
        Objects.requireNonNull(message, "message");
        return CompletableFuture.runAsync(() -> {
            Channel current = channel;
            if (!isConnected() || current == null || !current.isOpen()) {
                throw new java.util.concurrent.CompletionException(
                        new IllegalStateException("transport is disconnected"));
            }
            try {
                byte[] body = codec.encode(message).getBytes(StandardCharsets.UTF_8);
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .contentType("application/json").contentEncoding("UTF-8").deliveryMode(1).build();
                current.basicPublish(configuration.exchangeName(), RabbitMqRouting.forMessage(message),
                        properties, body);
            } catch (Exception error) {
                fire(new CollaborationTransportEvent.Error(error));
                throw new java.util.concurrent.CompletionException(error);
            }
        }, executor);
    }

    @Override public void addListener(CollaborationTransportListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override public void removeListener(CollaborationTransportListener listener) { listeners.remove(listener); }

    private void fire(CollaborationTransportEvent event) {
        for (CollaborationTransportListener listener : listeners) {
            try { listener.onTransportEvent(event); }
            catch (RuntimeException ignored) { /* isolate application listeners */ }
        }
    }

    private void closeResources() {
        Channel oldChannel = channel;
        Connection oldConnection = connection;
        channel = null;
        connection = null;
        try { if (oldChannel != null && oldChannel.isOpen()) oldChannel.close(); }
        catch (Exception ignored) { }
        try { if (oldConnection != null && oldConnection.isOpen()) oldConnection.close(); }
        catch (Exception ignored) { }
    }

    /** Permanently closes broker resources and daemon executors. */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            disconnect().join();
            executor.shutdownNow();
            try { executor.awaitTermination(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            listeners.clear();
        }
    }

    private static <T> CompletableFuture<T> failed(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }
}
