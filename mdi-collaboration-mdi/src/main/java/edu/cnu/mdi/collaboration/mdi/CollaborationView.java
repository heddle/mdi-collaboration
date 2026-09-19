package edu.cnu.mdi.collaboration.mdi;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import edu.cnu.mdi.collaboration.CollaborationService;
import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.event.CollaborationListener;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

/** Minimal MDI view proving asynchronous, Swing-safe message exchange. */
@SuppressWarnings("serial")
public final class CollaborationView extends BaseView {
    private final CollaborationService service;
    private final Collaborator recipient;
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JTextArea messages = new JTextArea();
    private final JTextField input = new JTextField(24);
    private final JButton sendButton = new JButton("Send");
    private final CollaborationListener swingListener;
    private boolean detached;

    /** Creates a view for one local participant and one demonstration recipient. */
    public CollaborationView(CollaborationService service, Collaborator recipient) {
        super(PropertyUtils.TITLE, "Collaboration — " + service.localCollaborator().displayName(),
                PropertyUtils.WIDTH, 480, PropertyUtils.HEIGHT, 330,
                PropertyUtils.USECONTAINER, false, PropertyUtils.VISIBLE, true);
        this.service = Objects.requireNonNull(service, "service");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.swingListener = new SwingCollaborationListener(this::handleEvent);
        buildContent();
        service.addListener(swingListener);
    }

    private void buildContent() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        header.add(new JLabel("To: " + recipient.displayName()), BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);

        messages.setEditable(false);
        messages.setLineWrap(true);
        messages.setWrapStyleWord(true);

        JPanel composer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        composer.add(input);
        composer.add(sendButton);
        sendButton.addActionListener(event -> send());
        input.addActionListener(event -> send());

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(messages), BorderLayout.CENTER);
        add(composer, BorderLayout.SOUTH);
    }

    private void send() {
        assertEdt();
        String content = input.getText().strip();
        if (content.isEmpty()) return;
        input.setText("");
        append(service.localCollaborator().displayName() + ": " + content);
        service.sendChat(recipient.id(), content).whenComplete((unused, error) -> {
            if (error != null) SwingUtilities.invokeLater(() -> append("Send failed: " + rootMessage(error)));
        });
    }

    private void handleEvent(CollaborationEvent event) {
        assertEdt();
        if (event instanceof CollaborationEvent.Connected) {
            statusLabel.setText("Connected");
        } else if (event instanceof CollaborationEvent.Disconnected) {
            statusLabel.setText("Disconnected");
        } else if (event instanceof CollaborationEvent.Error error) {
            statusLabel.setText("Error");
            append("Error: " + rootMessage(error.cause()));
        } else if (event instanceof CollaborationEvent.MessageReceived received) {
            CollaborationMessage message = received.message();
            if (message.type().equals(CollaborationMessageType.CHAT_MESSAGE)) {
                append(recipient.displayName() + ": " + message.content());
            }
        }
    }

    private void append(String line) { messages.append(line + System.lineSeparator()); }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("CollaborationView must be updated on the EDT");
        }
    }

    /** Detaches the service listener before MDI performs its normal view cleanup. */
    @Override public void prepareForExit() {
        detachListener();
        super.prepareForExit();
    }

    /** Also handles a view being disposed independently of application shutdown. */
    @Override public void dispose() {
        detachListener();
        super.dispose();
    }

    private void detachListener() {
        if (!detached && service != null && swingListener != null) {
            detached = true;
            service.removeListener(swingListener);
        }
    }

    String displayedMessages() { return messages.getText(); }
    String displayedStatus() { return statusLabel.getText(); }
    void setDraftAndSend(String text) { input.setText(text); sendButton.doClick(); }
}
