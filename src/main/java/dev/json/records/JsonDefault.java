package dev.json.records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the value of a record component if its JSON object member is missing, as JSON
 * text. The value is converted like a value read from the JSON object, so it can be given for
 * any component type, including records, collections and types with a {@link JsonDecoder}:
 * <pre>{@code
 * record Settings(@JsonDefault("10") int limit,
 *                 @JsonDefault("\"dark\"") String theme,
 *                 @JsonDefault("[]") List<String> tags) {}
 *
 * MAPPER.fromJson("{}", Settings.class);   // Settings[limit=10, theme=dark, tags=[]]
 * }</pre>
 * Only a missing member is replaced; JSON null is mapped as usual. The value is converted on
 * every use, so that, e.g., an array is not shared.
 * <p>
 * The annotation is not allowed on components of an optional type, which are empty if the
 * member is missing. JSON text that cannot be parsed, or a value that cannot be converted to the
 * type of the component, is a programming error and results in an
 * {@link IllegalArgumentException}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface JsonDefault {

    /**
     * {@return the default value of the component, as JSON text}
     */
    String value();
}
