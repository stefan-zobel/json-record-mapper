package dev.json.records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the name of the JSON object member that a record component is mapped from,
 * if it differs from the name of the component. It takes precedence over the
 * {@link JsonNaming} strategy of the {@link RecordMapper}:
 * <pre>{@code
 * record User(@JsonName("first_name") String firstName, int age) {}
 * }</pre>
 * The name must not be empty, and two components of a record must not be mapped from
 * the same member name.
 * <p>
 * On an enum constant, it specifies the JSON string that the constant is read from and
 * written as, also as the key of a map, instead of the name of the constant:
 * <pre>{@code
 * enum Color { @JsonName("red") RED, @JsonName("light-green") LIGHT_GREEN, BLUE }
 * }</pre>
 * Here, too, the name must not be empty, and two constants of an enum must not have the
 * same JSON name. On other fields, the annotation has no effect.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD })
public @interface JsonName {

    /**
     * {@return the name of the JSON object member, or the JSON string of an enum constant}
     */
    String value();
}
