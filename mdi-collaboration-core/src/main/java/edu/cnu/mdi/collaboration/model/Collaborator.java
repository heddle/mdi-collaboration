package edu.cnu.mdi.collaboration.model;

import java.util.Objects;
import java.util.UUID;

/** A known participant. Authentication is deliberately outside this value. */
public record Collaborator(UUID id, String displayName, String institution) {
    /** Creates and validates a collaborator. */
    public Collaborator {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        institution = institution == null ? "" : institution.strip();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }
}
