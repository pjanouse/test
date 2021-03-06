package dumper;

import org.jboss.dmr.Property;

import java.util.regex.Pattern;

/**
 * An element of a path specification for matching operations with addresses.
 * @author Brian Stansberry
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class PathElement {

    public static final String WILDCARD_VALUE = "*";

    private final String key;
    private final String value;
    private final boolean multiTarget;
    private final int hashCode;

    /**
     * A valid key contains alphanumerics and underscores, cannot start with a
     * number, and cannot start or end with {@code -}.
     */
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("\\*|[_a-zA-Z](?:[-_a-zA-Z0-9]*[_a-zA-Z0-9])?");

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     * @return the new path element
     */
    public static PathElement pathElement(final String key) {
        return new PathElement(key);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     * @return the new path element
     */
    public static PathElement pathElement(final String key, final String value) {
        return new PathElement(key, value);
    }

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     */
    PathElement(final String key) {
        this(key, WILDCARD_VALUE);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     */
    PathElement(final String key, final String value) {
        if (key == null || !VALID_KEY_PATTERN.matcher(key).matches()) {
            final String element = key + "=" + value;
            //throw new Exception(MESSAGES.invalidPathElementKey(element, key));
        }
        if (value == null) {
            final String element = key + "=" + value;
            //throw new Exception(MESSAGES.invalidPathElementValue(element, value, ' '));
        }
        boolean multiTarget = false;
        if(key.equals(WILDCARD_VALUE)) {
            this.key = WILDCARD_VALUE;
            multiTarget = true;
        } else {
            this.key = key;
        }
        if (value.equals(WILDCARD_VALUE)) {
            this.value = WILDCARD_VALUE;
            multiTarget = true;
        } else if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
            this.value = value.substring(1, value.length() - 1);
            multiTarget |= value.indexOf(',') != -1;
        } else {
            this.value = value;
        }
        this.multiTarget = multiTarget;
        hashCode = key.hashCode() * 19 + value.hashCode();
    }

    /**
     * Get the path key.
     * @return the path key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the path value.
     * @return the path value
     */
    public String getValue() {
        return value;
    }

    /**
     * Determine whether the given property matches this element.
     * @param property the property to check
     * @return {@code true} if the property matches
     */
    public boolean matches(Property property) {
        return property.getName().equals(key) && (value == WILDCARD_VALUE || property.getValue().asString().equals(value));
    }

    /**
     * Determine whether the value is the wildcard value.
     * @return {@code true} if the value is the wildcard value
     */
    public boolean isWildcard() {
        return WILDCARD_VALUE == value; //this is ok as we are expecting exact same object.
    }

    public boolean isMultiTarget() {
        return multiTarget;
    }

    public String[] getSegments() {
        return value.split(",");
    }

    public String[] getKeyValuePair(){
        return new String[]{key,value};
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine whether this object is equal to another.
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof PathElement && equals((PathElement) other);
    }

    /**
     * Determine whether this object is equal to another.
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(PathElement other) {
        return this == other || other != null && other.key.equals(key) && other.value.equals(value);
    }

    @Override
    public String toString() {
        return "\"" + key + "\" => \"" + value + "\"";
    }

}

