package com.socialpilot.app.domain.contract.secret;

/** Result of reading one reference. Only {@link Status#SUCCESS} carries a value and revision. */
public final class SecretRead {
    public enum Status {
        SUCCESS,
        /** Nothing stored, or the reference was deleted. */
        MISSING,
        /**
         * A record exists but cannot be used: Keystore key lost/invalidated, authentication failed,
         * unknown record version or an earlier write could not be confirmed. Ask the user to enter
         * or reconnect again; do not treat this as an invalid provider credential.
         */
        UNREADABLE
    }

    private static final SecretRead MISSING = new SecretRead(Status.MISSING, null, null);
    private static final SecretRead UNREADABLE = new SecretRead(Status.UNREADABLE, null, null);

    public final Status status;
    public final String value;
    public final String revision;

    private SecretRead(Status status, String value, String revision) {
        this.status = status;
        this.value = value;
        this.revision = revision;
    }

    public static SecretRead success(String value, String revision) {
        if (value == null || value.isEmpty() || revision == null || revision.isEmpty()) {
            throw new IllegalArgumentException("Incomplete secret");
        }
        return new SecretRead(Status.SUCCESS, value, revision);
    }

    public static SecretRead missing() {
        return MISSING;
    }

    public static SecretRead unreadable() {
        return UNREADABLE;
    }

    /** Never prints the value. */
    @Override
    public String toString() {
        return "SecretRead{" + status + "}";
    }
}
