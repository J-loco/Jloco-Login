package org.starloco.locos.login;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A Dofus client version such as "1.39.8e" (the "e" suffix marks the Electron client). */
public record ClientVersion(int major, int minor, int revision, boolean electron) implements Comparable<ClientVersion> {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(e?)");

    public static Optional<ClientVersion> parse(String text) {
        Matcher matcher = FORMAT.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ClientVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                !matcher.group(4).isEmpty()));
    }

    /** Numeric comparison; the Electron suffix does not matter. */
    @Override
    public int compareTo(ClientVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        if (result == 0) {
            result = Integer.compare(revision, other.revision);
        }
        return result;
    }

    public boolean isAtLeast(ClientVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + revision + (electron ? "e" : "");
    }
}
