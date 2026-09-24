package dev.json.records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the name that identifies a record of a sealed interface in the JSON object,
 * the simple name of the record class by default:
 * <pre>{@code
 * sealed interface Shape permits Circle, Square {}
 *
 * @JsonTypeName("circle")
 * record Circle(double radius) implements Shape {}
 *
 * // {"type": "circle", "radius": 1.0}
 * }</pre>
 * The name must not be empty, and the records of a sealed interface must have different
 * names.
 *
 * @see JsonDiscriminator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonTypeName {

    /**
     * {@return the name that identifies the record}
     */
    String value();
}
