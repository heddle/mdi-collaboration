package edu.cnu.mdi.collaboration;

/** Checked failure at a collaboration lifecycle or transport boundary. */
public class CollaborationException extends Exception {
    public CollaborationException(String message) { super(message); }
    public CollaborationException(String message, Throwable cause) { super(message, cause); }
}
