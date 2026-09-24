package dev.json.records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the name of the JSON object member that holds the type of a value of a sealed
 * interface, {@code "type"} by default:
 * <pre>{@code
 * @JsonDiscriminator("kind")
 * sealed interface Shape permits Circle, Square {}
 *
 * // {"kind": "Circle", "radius": 1.0}
 * }</pre>
 * The name must not be empty and must not be the JSON name of a component of a record of
 * the sealed interface. A sealed interface nested in another one, e.g.
 * {@code sealed interface Polygon extends Shape}, must use the same name.
 *
 * @see JsonTypeName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonDiscriminator {

    /**
     * {@return the name of the JSON object member that holds the type}
     */
    String value();
}
