package dev.json.records;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

// Sealed interfaces whose permitted subclasses are records (possibly through nested sealed
// interfaces). A value of such an interface is a JSON object with a member, the discriminator,
// that holds the type name of the record. As this only depends on annotations, the results
// are cached globally.
final class SealedTypes {

    static final String DEFAULT_DISCRIMINATOR = "type";

    // the records of a sealed interface by type name, in declaration order
    record Hierarchy(Class<?> sealedInterface, String discriminator, Map<String, Class<?>> types) {}

    // the discriminator member and the type name of a record of a sealed interface
    record Discriminator(String name, String typeName) {}

    private static final ClassValue<Hierarchy> HIERARCHIES = new ClassValue<>() {
        @Override
        protected Hierarchy computeValue(Class<?> type) {
            return buildHierarchy(type);
        }
    };

    private static final ClassValue<Optional<Discriminator>> DISCRIMINATORS = new ClassValue<>() {
        @Override
        protected Optional<Discriminator> computeValue(Class<?> type) {
            return buildDiscriminator(type);
        }
    };

    private SealedTypes() {
        // no instances
    }

    static boolean isSealedInterface(Class<?> type) {
        return type.isInterface() && type.isSealed();
    }

    // the validated hierarchy of a sealed interface
    static Hierarchy hierarchy(Class<?> sealedInterface) {
        return HIERARCHIES.get(sealedInterface);
    }

    // the discriminator of a record, if it implements a sealed interface
    static Optional<Discriminator> discriminator(Class<?> record) {
        return DISCRIMINATORS.get(record);
    }

    private static String discriminatorName(Class<?> sealedInterface) {
        var annotation = sealedInterface.getAnnotation(JsonDiscriminator.class);
        if (annotation == null) {
            return DEFAULT_DISCRIMINATOR;
        }
        if (annotation.value().isEmpty()) {
            throw new IllegalArgumentException("Empty @JsonDiscriminator on " + sealedInterface.getName());
        }
        return annotation.value();
    }

    private static String typeName(Class<?> record) {
        var annotation = record.getAnnotation(JsonTypeName.class);
        if (annotation == null) {
            return record.getSimpleName();
        }
        if (annotation.value().isEmpty()) {
            throw new IllegalArgumentException("Empty @JsonTypeName on " + record.getName());
        }
        return annotation.value();
    }

    private static Hierarchy buildHierarchy(Class<?> sealedInterface) {
        var discriminator = discriminatorName(sealedInterface);
        var types = new LinkedHashMap<String, Class<?>>();
        addPermittedSubclasses(sealedInterface, sealedInterface, discriminator, types);
        return new Hierarchy(sealedInterface, discriminator, Collections.unmodifiableMap(types));
    }

    private static void addPermittedSubclasses(Class<?> root, Class<?> sealedInterface, String discriminator,
                                               Map<String, Class<?>> types) {
        if (sealedInterface.getTypeParameters().length > 0) {
            throw new UnsupportedOperationException("Generic sealed interface " + sealedInterface.getName()
                    + " is not supported");
        }
        if (!discriminatorName(sealedInterface).equals(discriminator)) {
            throw new IllegalArgumentException("Sealed interface " + sealedInterface.getName()
                    + " has another discriminator than " + root.getName() + " (\"" + discriminator + "\")");
        }
        for (var subclass : sealedInterface.getPermittedSubclasses()) {
            if (subclass.isRecord()) {
                if (subclass.getTypeParameters().length > 0) {
                    throw new UnsupportedOperationException("Generic record " + subclass.getName()
                            + " of sealed interface " + root.getName() + " is not supported");
                }
                var name = typeName(subclass);
                var previous = types.putIfAbsent(name, subclass);
                if (previous != null && previous != subclass) {
                    throw new IllegalArgumentException("Records " + previous.getName() + " and " + subclass.getName()
                            + " of sealed interface " + root.getName() + " have the same type name \"" + name + "\"");
                }
            } else if (isSealedInterface(subclass)) {
                addPermittedSubclasses(root, subclass, discriminator, types);
            } else {
                throw new UnsupportedOperationException("Permitted subclass " + subclass.getName()
                        + " of sealed interface " + root.getName() + " is neither a record nor a sealed interface");
            }
        }
    }

    private static Optional<Discriminator> buildDiscriminator(Class<?> record) {
        // all sealed interfaces the record implements, directly or through other interfaces
        var names = new TreeSet<String>();
        var visited = new HashSet<Class<?>>();
        var interfaces = new ArrayDeque<Class<?>>(List.of(record.getInterfaces()));
        while (!interfaces.isEmpty()) {
            var type = interfaces.pop();
            if (!visited.add(type)) {
                continue;
            }
            if (isSealedInterface(type)) {
                names.add(hierarchy(type).discriminator());
            }
            interfaces.addAll(List.of(type.getInterfaces()));
        }
        if (names.isEmpty()) {
            return Optional.empty();
        }
        if (names.size() > 1) {
            throw new IllegalArgumentException("Record " + record.getName()
                    + " implements sealed interfaces with different discriminators " + names);
        }
        return Optional.of(new Discriminator(names.first(), typeName(record)));
    }
}
