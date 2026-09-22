package edu.cnu.mdi.collaboration.mdi;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
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
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchange;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchangeEvent;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchangeListener;
import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

/** Minimal MDI view proving asynchronous, Swing-safe message exchange. */
@SuppressWarnings("serial")
public final class CollaborationView extends BaseView {
    private final CollaborationService service;
    private final Collaborator recipient;
    private final CollaborationFileExchange fileExchange;
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JTextArea messages = new JTextArea();
    private final JTextField input = new JTextField(24);
    private final JButton sendButton = new JButton("Send");
    private final JButton sendFileButton = new JButton("Send File…");
    private final JButton acceptFileButton = new JButton("Accept…");
    private final JButton rejectFileButton = new JButton("Reject");
    private final DefaultListModel<FileOffer> pendingOffers = new DefaultListModel<>();
    private final JList<FileOffer> offerList = new JList<>(pendingOffers);
    private final CollaborationListener swingListener;
    private final CollaborationFileExchangeListener swingFileListener;
    private boolean detached;

    /** Creates a view for one local participant and one demonstration recipient. */
    public CollaborationView(CollaborationService service, Collaborator recipient) {
        this(service, recipient, null);
    }

    /** Creates a view with optional file-offer controls. */
    public CollaborationView(CollaborationService service, Collaborator recipient,
            CollaborationFileExchange fileExchange) {
        super(PropertyUtils.TITLE, "Collaboration — " + service.localCollaborator().displayName(),
                PropertyUtils.WIDTH, 620, PropertyUtils.HEIGHT, 360,
                PropertyUtils.USECONTAINER, false, PropertyUtils.VISIBLE, true);
        this.service = Objects.requireNonNull(service, "service");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.fileExchange = fileExchange;
        this.swingListener = new SwingCollaborationListener(this::handleEvent);
        this.swingFileListener = fileExchange == null ? null
                : new SwingFileExchangeListener(this::handleFileEvent);
        buildContent();
        service.addListener(swingListener);
        if (fileExchange != null) fileExchange.addListener(swingFileListener);
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
        if (fileExchange != null) composer.add(sendFileButton);
        sendButton.addActionListener(event -> send());
        input.addActionListener(event -> send());
        sendFileButton.addActionListener(event -> chooseAndOfferFile());

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(messages), BorderLayout.CENTER);
        if (fileExchange != null) add(buildOffersPanel(), BorderLayout.EAST);
        add(composer, BorderLayout.SOUTH);
    }

    private JPanel buildOffersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Pending file offers"));
        offerList.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(value.fileName() + " (" + value.size() + " bytes)");
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        panel.add(new JScrollPane(offerList), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(acceptFileButton);
        actions.add(rejectFileButton);
        acceptFileButton.addActionListener(event -> chooseAndAccept());
        rejectFileButton.addActionListener(event -> rejectSelected());
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
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

    private void chooseAndOfferFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            offerFile(chooser.getSelectedFile().toPath());
        }
    }

    private void chooseAndAccept() {
        FileOffer offer = offerList.getSelectedValue();
        if (offer == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            acceptOffer(offer, chooser.getSelectedFile().toPath());
        }
    }

    private void rejectSelected() {
        FileOffer offer = offerList.getSelectedValue();
        if (offer == null) return;
        fileExchange.reject(offer.transferId(), "Rejected by recipient");
        pendingOffers.removeElement(offer);
    }

    private void handleFileEvent(CollaborationFileExchangeEvent event) {
        assertEdt();
        if (event instanceof CollaborationFileExchangeEvent.OfferReceived received) {
            pendingOffers.addElement(received.offer());
            append("File offered: " + received.offer().fileName());
        } else if (event instanceof CollaborationFileExchangeEvent.OfferSent sent) {
            append("File offer sent: " + sent.offer().fileName());
        } else if (event instanceof CollaborationFileExchangeEvent.OfferAccepted accepted) {
            append("File offer accepted: " + accepted.transferId());
        } else if (event instanceof CollaborationFileExchangeEvent.OfferRejected rejected) {
            append("File offer rejected: " + Objects.toString(rejected.reason(), ""));
        } else if (event instanceof CollaborationFileExchangeEvent.TransferCompleted completed) {
            append("File transfer complete: " + completed.path());
        } else if (event instanceof CollaborationFileExchangeEvent.Error error) {
            append("File transfer failed: " + rootMessage(error.cause()));
        }
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
            if (fileExchange != null && swingFileListener != null) {
                fileExchange.removeListener(swingFileListener);
            }
        }
    }

    String displayedMessages() { return messages.getText(); }
    String displayedStatus() { return statusLabel.getText(); }
    void setDraftAndSend(String text) { input.setText(text); sendButton.doClick(); }
    int pendingOfferCount() { return pendingOffers.size(); }
    FileOffer firstPendingOffer() { return pendingOffers.isEmpty() ? null : pendingOffers.firstElement(); }
    void offerFile(Path file) { fileExchange.offer(file, recipient); }
    void acceptOffer(FileOffer offer, Path destination) {
        fileExchange.accept(offer.transferId(), destination);
        pendingOffers.removeElement(offer);
    }
}
