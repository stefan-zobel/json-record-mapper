package dev.json.records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a record component from the JSON form of its record: the component is not written,
 * and it is not read, even if the JSON object has a member of its name. When a record is read,
 * the component gets its {@link JsonDefault} value, or else {@code null}, zero, {@code false}
 * or an empty optional:
 * <pre>{@code
 * record User(String name, @JsonIgnore String password) {}
 *
 * MAPPER.toJsonText(new User("Alice", "secret"));   // {"name":"Alice"}
 * }</pre>
 * In the strict mode ({@link RecordMapper.Builder#failOnUnknownMembers(boolean)}), a member
 * with the name of an ignored component is unknown.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface JsonIgnore {
}
