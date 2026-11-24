/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.reddit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a reloaded archive model to the bound model. Only deserialized fields that changed
 * during recovery are copied, so votes/saves changed while the request was in flight and
 * comment-tree state owned by the UI are kept.
 */
final class ArchiveRetryMerge {
    private static final String[] RECOVERY_STATE = {
            ArchiveRecoveryState.EXTRA_EMOJI_CONTEXT_KEY, ArchiveRecoveryState.RETRY_KEY,
    };
    private static final Map<String, Field[]> resolved = new HashMap<>();

    private ArchiveRetryMerge() {}

    /**
     * @param fields space-separated {@code Lowner;->name:type} descriptors of the fields set by
     *               the native deserializer.
     */
    static synchronized void apply(Object target, Object before, Object after, String fields)
            throws ReflectiveOperationException {
        String key = target.getClass().getName() + ' ' + fields;
        Field[] changed = resolved.get(key);
        if (changed == null) {
            changed = resolve(target.getClass(), fields);
            resolved.put(key, changed);
        }
        for (Field field : changed) {
            Object value = field.get(after);
            if (!Objects.equals(field.get(before), value)) field.set(target, value);
        }
        // Recovery state is not user state. The fresh model is authoritative.
        for (String name : RECOVERY_STATE) {
            Field field = find(target.getClass(), null, name);
            field.set(target, field.get(after));
        }
    }

    private static Field[] resolve(Class<?> type, String fields) throws NoSuchFieldException {
        List<Field> result = new ArrayList<>();
        for (String descriptor : fields.split(" ")) {
            if (descriptor.isEmpty()) continue;
            int arrow = descriptor.indexOf("->");
            int colon = descriptor.indexOf(':', arrow);
            String owner = descriptor.substring(1, arrow - 1).replace('/', '.');
            result.add(find(type, owner, descriptor.substring(arrow + 2, colon)));
        }
        if (result.isEmpty()) throw new IllegalStateException("No archive fields for " + type);
        return result.toArray(new Field[0]);
    }

    private static Field find(Class<?> type, String owner, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (owner != null && !current.getName().equals(owner)) continue;
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                if (owner != null) break;
            }
        }
        throw new NoSuchFieldException((owner == null ? type.getName() : owner) + "." + name);
    }
}
