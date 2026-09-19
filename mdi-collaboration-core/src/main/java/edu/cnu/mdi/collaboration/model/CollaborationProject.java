package edu.cnu.mdi.collaboration.model;

import java.util.Objects;
import java.util.UUID;

/** Identifies a research collaboration project. */
public record CollaborationProject(UUID id, String name) {
    /** Creates and validates a project. */
    public CollaborationProject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name = name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("name must not be blank");
    }
}
