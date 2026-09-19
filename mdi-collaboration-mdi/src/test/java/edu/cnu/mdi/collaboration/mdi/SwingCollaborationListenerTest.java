package edu.cnu.mdi.collaboration.mdi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.event.CollaborationEvent;

class SwingCollaborationListenerTest {
    @Test void workerThreadEventIsMarshaledToEdt() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicBoolean wasEdt = new AtomicBoolean();
        SwingCollaborationListener listener = new SwingCollaborationListener(event -> {
            wasEdt.set(SwingUtilities.isEventDispatchThread());
            invoked.countDown();
        });

        Thread worker = new Thread(() -> listener.onCollaborationEvent(new CollaborationEvent.Connected()));
        worker.start();
        worker.join();

        assertTrue(invoked.await(2, TimeUnit.SECONDS));
        assertTrue(wasEdt.get());
    }

    @Test void eventAlreadyOnEdtIsDeliveredSynchronously() throws Exception {
        AtomicBoolean delivered = new AtomicBoolean();
        SwingCollaborationListener listener = new SwingCollaborationListener(event -> delivered.set(true));
        SwingUtilities.invokeAndWait(() -> {
            listener.onCollaborationEvent(new CollaborationEvent.Disconnected());
            assertTrue(delivered.get());
        });
    }
}
