package edu.cnu.mdi.collaboration.transfer;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Metadata offering a file through a named out-of-band transfer mechanism. */
public record FileOffer(UUID transferId, UUID senderId, UUID recipientId,
        String fileName, long size, String sha256, String mechanism,
        Map<String, String> parameters) {

    /** Creates and validates an untrusted file offer. */
    public FileOffer {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        fileName = validateFileName(fileName);
        if (size < 0) throw new IllegalArgumentException("size must not be negative");
        sha256 = normalizeChecksum(sha256);
        mechanism = requireToken(mechanism, "mechanism");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** Validates and returns a safe leaf filename. */
    public static String validateFileName(String candidate) {
        Objects.requireNonNull(candidate, "fileName");
        String value = candidate.strip();
        if (value.isEmpty() || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("fileName must be a safe leaf name");
        }
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || path.getNameCount() != 1) {
                throw new IllegalArgumentException("fileName must be a safe leaf name");
            }
        } catch (InvalidPathException error) {
            throw new IllegalArgumentException("fileName is invalid", error);
        }
        return value;
    }

    private static String normalizeChecksum(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        }
        return normalized;
    }

    private static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        String token = value.strip().toLowerCase(Locale.ROOT);
        if (!token.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return token;
    }
}
