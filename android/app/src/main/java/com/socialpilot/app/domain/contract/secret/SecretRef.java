package com.socialpilot.app.domain.contract.secret;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque credential reference. Room and other non-secret records may persist {@link #opaque()},
 * never the secret value. The identifier must not contain the secret or user-visible names.
 */
public final class SecretRef {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String SCHEME = "secret://";

    public final SecretNamespace namespace;
    public final String id;

    public SecretRef(SecretNamespace namespace, String id) {
        this.namespace = Objects.requireNonNull(namespace);
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid secret id");
        this.id = id;
    }

    /** Stable text form, for example {@code secret://social/3f2c...}. */
    public String opaque() {
        return SCHEME + namespace.wireName + "/" + id;
    }

    public static SecretRef parse(String opaque) {
        if (opaque == null || !opaque.startsWith(SCHEME)) throw new IllegalArgumentException("Invalid secret reference");
        String rest = opaque.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) throw new IllegalArgumentException("Invalid secret reference");
        return new SecretRef(SecretNamespace.fromWireName(rest.substring(0, slash)), rest.substring(slash + 1));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecretRef)) return false;
        SecretRef that = (SecretRef) other;
        return namespace == that.namespace && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, id);
    }

    /** Never prints secret material; the reference itself is not secret. */
    @Override
    public String toString() {
        return opaque();
    }
}
