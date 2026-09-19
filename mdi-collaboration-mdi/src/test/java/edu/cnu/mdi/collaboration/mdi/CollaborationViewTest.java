package edu.cnu.mdi.collaboration.mdi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.CollaborationService;
import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationBus;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationTransport;

class CollaborationViewTest {
    @Test void viewsSendAndReceiveWithoutWorkerThreadSwingAccess() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        try (var bus = new InMemoryCollaborationBus(); var a = service(alice, bus); var b = service(bob, bus)) {
            CollaborationView[] views = new CollaborationView[2];
            SwingUtilities.invokeAndWait(() -> {
                views[0] = new CollaborationView(a, bob);
                views[1] = new CollaborationView(b, alice);
            });
            a.connect().join(); b.connect().join();
            flushEdt();
            assertEquals("Connected", onEdt(views[0]::displayedStatus));
            assertEquals("Connected", onEdt(views[1]::displayedStatus));

            SwingUtilities.invokeAndWait(() -> views[0].setDraftAndSend("hello Bob"));
            awaitText(views[1], "Alice: hello Bob");
            SwingUtilities.invokeAndWait(() -> views[1].setDraftAndSend("hello Alice"));
            awaitText(views[0], "Bob: hello Alice");

            SwingUtilities.invokeAndWait(() -> { views[0].dispose(); views[1].dispose(); });
        }
    }

    @Test void disposedViewNoLongerReceivesServiceEvents() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        try (var bus = new InMemoryCollaborationBus(); var a = service(alice, bus); var b = service(bob, bus)) {
            CollaborationView[] view = new CollaborationView[1];
            SwingUtilities.invokeAndWait(() -> {
                view[0] = new CollaborationView(b, alice);
                view[0].dispose();
            });
            a.connect().join(); b.connect().join();
            a.sendChat(bob.id(), "after disposal").join();
            Thread.sleep(100);
            flushEdt();
            assertFalse(onEdt(view[0]::displayedMessages).contains("after disposal"));
        }
    }

    private static Collaborator person(String name) {
        return new Collaborator(UUID.randomUUID(), name, "CNU");
    }

    private static CollaborationService service(Collaborator collaborator, InMemoryCollaborationBus bus) {
        return new CollaborationService(collaborator,
                new InMemoryCollaborationTransport(collaborator.id(), bus));
    }

    private static void awaitText(CollaborationView view, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (onEdt(view::displayedMessages).contains(expected)) return;
            Thread.sleep(10);
        }
        fail("Did not display: " + expected);
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        return result.get();
    }
}
