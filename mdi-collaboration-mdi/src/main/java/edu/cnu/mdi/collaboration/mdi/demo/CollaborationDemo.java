package edu.cnu.mdi.collaboration.mdi.demo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.collaboration.CollaborationService;
import edu.cnu.mdi.collaboration.mdi.CollaborationView;
import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.rabbitmq.RabbitMqCollaborationTransport;
import edu.cnu.mdi.collaboration.rabbitmq.RabbitMqConfiguration;
import edu.cnu.mdi.collaboration.transport.CollaborationTransport;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationBus;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationTransport;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchange;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferRegistry;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferService;
import edu.cnu.mdi.util.PropertyUtils;

/** One-process, two-client MDI demonstration using the in-memory transport. */
@SuppressWarnings("serial")
public final class CollaborationDemo extends BaseMDIApplication {
    private static CollaborationDemo instance;
    // BaseMDIApplication invokes addInitialViews() from its constructor, before
    // subclass field initializers run. Initialize this resource in that hook.
    private InMemoryCollaborationBus bus;
    private CollaborationService aliceService;
    private CollaborationService bobService;
    private LocalFileTransferRegistry fileRegistry;
    private CollaborationFileExchange aliceFileExchange;
    private CollaborationFileExchange bobFileExchange;

    private CollaborationDemo() {
        super(PropertyUtils.TITLE, "MDI Collaboration Demo", PropertyUtils.FRACTION, 0.75,
                PropertyUtils.CONSOLELOG, true);
    }

    /** Returns the demo's single MDI application instance. */
    public static CollaborationDemo getInstance() {
        if (instance == null) instance = new CollaborationDemo();
        return instance;
    }

    @Override protected void addInitialViews() {
        if ("rabbitmq".equalsIgnoreCase(System.getProperty("collaboration.transport", "memory"))) {
            addRabbitMqView();
            return;
        }
        addInMemoryViews();
    }

    private void addInMemoryViews() {
        bus = new InMemoryCollaborationBus();
        Collaborator alice = participant("Alice");
        Collaborator bob = participant("Bob");
        aliceService = service(alice);
        bobService = service(bob);
        fileRegistry = new LocalFileTransferRegistry();
        aliceFileExchange = new CollaborationFileExchange(aliceService,
                new LocalFileTransferService(alice.id(), fileRegistry));
        bobFileExchange = new CollaborationFileExchange(bobService,
                new LocalFileTransferService(bob.id(), fileRegistry));
        CollaborationView aliceView = new CollaborationView(aliceService, bob, aliceFileExchange);
        CollaborationView bobView = new CollaborationView(bobService, alice, bobFileExchange);
        aliceView.setLocation(30, 30);
        bobView.setLocation(540, 30);
        aliceService.connect();
        bobService.connect();
    }

    private void addRabbitMqView() {
        String localName = System.getProperty("collaboration.user", "Alice").strip();
        String peerName = System.getProperty("collaboration.peer",
                localName.equalsIgnoreCase("Alice") ? "Bob" : "Alice").strip();
        Collaborator local = participant(localName);
        Collaborator peer = participant(peerName);
        CollaborationTransport transport = new RabbitMqCollaborationTransport(local.id(),
                RabbitMqConfiguration.fromEnvironment());
        aliceService = new CollaborationService(local, transport);
        new CollaborationView(aliceService, peer).setLocation(30, 30);
        aliceService.connect();
    }

    private static Collaborator participant(String name) {
        if (name.isBlank()) throw new IllegalArgumentException("collaboration user names must not be blank");
        UUID id = UUID.nameUUIDFromBytes(("mdi-collaboration-demo:" + name.toLowerCase())
                .getBytes(StandardCharsets.UTF_8));
        return new Collaborator(id, name, "Demo");
    }

    private CollaborationService service(Collaborator collaborator) {
        return new CollaborationService(collaborator,
                new InMemoryCollaborationTransport(collaborator.id(), bus));
    }

    @Override protected void prepareForShutdown() {
        try {
            if (aliceFileExchange != null) aliceFileExchange.close();
            if (bobFileExchange != null) bobFileExchange.close();
            if (aliceService != null) aliceService.close();
            if (bobService != null) bobService.close();
            if (fileRegistry != null) fileRegistry.close();
            if (bus != null) bus.close();
        } finally {
            super.prepareForShutdown();
        }
    }

    @Override protected boolean exitOnClose() { return true; }

    /** Starts the demonstration on the EDT. */
    public static void main(String[] args) { BaseMDIApplication.launch(CollaborationDemo::getInstance); }
}
