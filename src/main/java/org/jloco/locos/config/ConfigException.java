package org.jloco.locos.config;

/** The configuration is missing or invalid; the message says what to fix. */
public final class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
