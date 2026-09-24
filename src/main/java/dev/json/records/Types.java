package dev.json.records;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// Generic types of record components: substitution of type variables and canonical
// parameterized types, which are used as cache keys for the decoders of generic records
final class Types {

    private Types() {
        // no instances
    }

    // Immutable ParameterizedType whose equals and hashCode are compatible with the
    // implementation of the JDK, so parameterized types of both are interchangeable keys
    static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> rawType;
        private final Type ownerType;
        private final Type[] typeArguments;
        private final int hash;

        ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] typeArguments) {
            this.rawType = rawType;
            this.ownerType = ownerType;
            this.typeArguments = typeArguments;
            this.hash = Arrays.hashCode(typeArguments) ^ Objects.hashCode(ownerType) ^ rawType.hashCode();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }

        @Override
        public Class<?> getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ParameterizedType type
                    && rawType.equals(type.getRawType())
                    && Objects.equals(ownerType, type.getOwnerType())
                    && Arrays.equals(typeArguments, type.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String getTypeName() {
            return Arrays.stream(typeArguments).map(Type::getTypeName)
                    .collect(Collectors.joining(", ", rawType.getTypeName() + "<", ">"));
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    // Replaces the type variables of the given type by their bindings. Parameterized types
    // are rebuilt as ParameterizedTypeImpl. Wildcards, generic array types and type
    // variables without a binding are not supported.
    static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof Class<?>) {
            return type;
        }
        if (type instanceof TypeVariable<?> variable && bindings.containsKey(variable)) {
            return bindings.get(variable);
        }
        if (type instanceof ParameterizedType parameterized) {
            var arguments = parameterized.getActualTypeArguments();
            var resolved = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                var argument = arguments[i];
                if (argument instanceof WildcardType || argument instanceof GenericArrayType
                        || argument instanceof TypeVariable<?> variable && !bindings.containsKey(variable)) {
                    // reported with the enclosing type, e.g. java.util.List<?>
                    throw new UnsupportedOperationException("Unsupported type: " + type.getTypeName());
                }
                resolved[i] = resolve(argument, bindings);
            }
            return new ParameterizedTypeImpl((Class<?>) parameterized.getRawType(), parameterized.getOwnerType(),
                    resolved);
        }
        throw new UnsupportedOperationException("Unsupported type: " + type.getTypeName());
    }

    // returns the type with all parameterized types rebuilt as ParameterizedTypeImpl
    static Type canonicalize(Type type) {
        return resolve(type, Map.of());
    }

    // returns the class of a class or of a parameterized type
    static Class<?> rawClass(Type type) {
        return type instanceof ParameterizedType parameterized
                ? (Class<?>) parameterized.getRawType()
                : (Class<?>) type;
    }
}
