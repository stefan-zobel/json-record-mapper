package dev.json.records;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A reference to a record type with type arguments, such as {@code Page<User>}, which
 * a {@link Class} cannot express. It is used to map a JSON value to a generic record at
 * the top level, see {@link RecordMapper#fromTyped(java21.util.json.JsonObject, TypeRef)}.
 * <p>
 * A {@code TypeRef} is usually created as an anonymous subclass, which captures the
 * type argument at compile time:
 * <pre>{@code
 * record Page<T>(List<T> items, int total) {}
 *
 * Page<User> page = MAPPER.fromTyped(json, new TypeRef<Page<User>>() {});
 * }</pre>
 * In generic code, where a type variable is erased at runtime, the type is composed
 * with {@link #of(Class, Type...)} instead:
 * <pre>{@code
 * <T extends Record> Page<T> load(JsonObject json, Class<T> type) {
 *   return MAPPER.fromTyped(json, TypeRef.of(Page.class, type));
 * }
 * }</pre>
 * The type must not contain type variables or wildcards. Two {@code TypeRef}s are equal
 * if they refer to the same type.
 *
 * @param <T> the referenced record type
 */
public abstract class TypeRef<T extends Record> {
    private final Type type;

    /**
     * Captures the type argument of the anonymous subclass.
     *
     * @throws IllegalStateException    if the subclass does not specify a type argument.
     * @throws IllegalArgumentException if the type argument contains a type variable or a
     *                                  wildcard, or is a generic record without type arguments.
     */
    protected TypeRef() {
        if (!(getClass().getGenericSuperclass() instanceof ParameterizedType superclass)
                || superclass.getRawType() != TypeRef.class) {
            throw new IllegalStateException(
                    "TypeRef must be created with a type argument, e.g. new TypeRef<Page<User>>() {}");
        }
        this.type = checked(superclass.getActualTypeArguments()[0]);
    }

    private TypeRef(Type type) {
        this.type = checked(type);
    }

    /**
     * Returns a {@code TypeRef} for the given record class with the given type arguments,
     * for example {@code TypeRef.of(Page.class, User.class)} for {@code Page<User>}.
     * A type argument may be a class or a parameterized type, e.g. the {@link #type()} of
     * another {@code TypeRef}. The type arguments are checked at runtime, the type
     * parameter {@code T} of the result is not.
     *
     * @param <T>           The referenced record type.
     * @param rawType       The record class. Must not be null.
     * @param typeArguments One type argument for each type parameter of the record class.
     * @return A {@code TypeRef} for the parameterized record type.
     * @throws NullPointerException     if {@code rawType} or a type argument is null.
     * @throws IllegalArgumentException if {@code rawType} is not a record, the number of type
     *                                  arguments does not match, or a type argument is
     *                                  primitive, contains a type variable or a wildcard.
     */
    public static <T extends Record> TypeRef<T> of(Class<?> rawType, Type... typeArguments) {
        Objects.requireNonNull(rawType, "rawType is null");
        if (!rawType.isRecord()) {
            throw new IllegalArgumentException(rawType.getName() + " is not a record");
        }
        var parameters = rawType.getTypeParameters();
        if (parameters.length != typeArguments.length) {
            throw new IllegalArgumentException("Record " + rawType.getName() + " has " + parameters.length
                    + " type parameter(s), but " + typeArguments.length + " type argument(s) were given");
        }
        for (var argument : typeArguments) {
            Objects.requireNonNull(argument, "type argument is null");
            if (argument instanceof Class<?> clazz && clazz.isPrimitive()) {
                throw new IllegalArgumentException("Primitive type argument: " + clazz.getName());
            }
        }
        // the owner type is chosen like by the JDK, so that equal types are equal
        var type = typeArguments.length == 0
                ? rawType
                : new Types.ParameterizedTypeImpl(rawType, rawType.getDeclaringClass(), typeArguments.clone());
        return new TypeRef<T>(type) {};
    }

    private static Type checked(Type type) {
        Type canonical;
        try {
            canonical = Types.canonicalize(type);
        } catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException(
                    "Type " + type.getTypeName() + " contains a type variable or a wildcard, use TypeRef.of", e);
        }
        var raw = Types.rawClass(canonical);
        if (!raw.isRecord()) {
            throw new IllegalArgumentException(raw.getName() + " is not a record");
        }
        if (canonical instanceof Class<?> && raw.getTypeParameters().length > 0) {
            throw new IllegalArgumentException("Generic record " + raw.getName() + " requires type arguments");
        }
        return canonical;
    }

    /**
     * {@return the referenced type, a record class or a parameterized record type}
     */
    public Type type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TypeRef<?> other && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return "TypeRef<" + type.getTypeName() + ">";
    }
}
