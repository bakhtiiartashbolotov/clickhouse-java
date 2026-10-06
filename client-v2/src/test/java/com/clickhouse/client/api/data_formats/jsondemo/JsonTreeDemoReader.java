package com.clickhouse.client.api.data_formats.jsondemo;

import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.ClickHouseDataType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/** Experimental metadata-capture reader; not a production JSON reader or JDBC API. */
final class JsonTreeDemoReader extends BinaryStreamReader {
    private final Map<Object[], String[]> tupleNames = new IdentityHashMap<>();
    private final Set<Map<?, ?>> maps = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean capturing;

    JsonTreeDemoReader(InputStream input) {
        super(input, TimeZone.getTimeZone("UTC"), null, new DefaultByteBufferAllocator(),
                false, Collections.emptyMap(), false);
    }

    @Override
    public Object[] readTuple(ClickHouseColumn column) throws IOException {
        Object[] values = super.readTuple(column);
        if (!capturing) {
            return values;
        }
        String[] names = new String[column.getNestedColumns().size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = column.getNestedColumns().get(i).getColumnName();
        }
        tupleNames.put(values, names);
        return values;
    }

    @Override
    public Map<?, ?> readMap(ClickHouseColumn column) throws IOException {
        Map<?, ?> value = super.readMap(column);
        if (capturing) {
            maps.add(value);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    JsonElement readJson(ClickHouseColumn column) throws IOException {
        if (column.getDataType() != ClickHouseDataType.JSON) {
            throw new IllegalArgumentException("Expected a JSON column");
        }
        capturing = true;
        try {
            Object value = readValue(column);
            if (value == null) {
                return null;
            }
            Map<String, Object> paths = (Map<String, Object>) value;
            JsonObject result = new JsonObject();
            Set<JsonObject> branches = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Map.Entry<String, Object> entry : new TreeMap<>(paths).entrySet()) {
                addPath(result, branches, entry.getKey(), project(entry.getValue()));
            }
            return result;
        } finally {
            capturing = false;
            tupleNames.clear();
            maps.clear();
        }
    }

    private JsonElement project(Object value) {
        JsonArray result = new JsonArray();
        Deque<Work> pending = new ArrayDeque<>();
        pending.push(new Work(value, result, null));
        while (!pending.isEmpty()) {
            Work work = pending.pop();
            Object current = work.value;
            JsonElement node;
            if (current == null) {
                node = JsonNull.INSTANCE;
            } else if (current instanceof String) {
                node = new JsonPrimitive((String) current);
            } else if (current instanceof Boolean) {
                node = new JsonPrimitive((Boolean) current);
            } else if (current instanceof Number) {
                if (current instanceof Float || current instanceof Double) {
                    double number = ((Number) current).doubleValue();
                    if (Double.isNaN(number) || Double.isInfinite(number)) {
                        throw new IllegalArgumentException("Non-finite numbers are outside this demo");
                    }
                }
                node = new JsonPrimitive((Number) current);
            } else if (current instanceof ArrayValue) {
                ArrayValue array = (ArrayValue) current;
                node = new JsonArray();
                for (int i = array.length() - 1; i >= 0; i--) {
                    pending.push(new Work(array.get(i), node, null));
                }
            } else if (current instanceof Object[]) {
                Object[] tuple = (Object[]) current;
                String[] names = tupleNames.get(tuple);
                if (names == null) {
                    throw new IllegalArgumentException("Tuple metadata is unavailable");
                }
                boolean named = names.length > 0 && !names[0].isEmpty();
                node = named ? new JsonObject() : new JsonArray();
                for (int i = tuple.length - 1; i >= 0; i--) {
                    pending.push(new Work(tuple[i], node, named ? names[i] : null));
                }
            } else if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (!map.isEmpty() && !maps.contains(map)) {
                    throw new IllegalArgumentException("Nested JSON is outside this demo");
                }
                node = new JsonObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("Non-string Map keys are outside this demo");
                    }
                    pending.push(new Work(entry.getValue(), node, (String) entry.getKey()));
                }
            } else {
                throw new IllegalArgumentException("Unsupported demo value: " + current.getClass().getName());
            }
            if (work.parent.isJsonObject()) {
                work.parent.getAsJsonObject().add(work.name, node);
            } else {
                work.parent.getAsJsonArray().add(node);
            }
        }
        return result.get(0);
    }

    private void addPath(JsonObject root, Set<JsonObject> branches, String path, JsonElement value) {
        String[] parts = path.split("\\.", -1);
        JsonObject parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement child = parent.get(parts[i]);
            if (child == null) {
                JsonObject object = new JsonObject();
                branches.add(object);
                parent.add(parts[i], object);
                parent = object;
            } else if (child.isJsonObject() && branches.contains(child.getAsJsonObject())) {
                parent = child.getAsJsonObject();
            } else {
                throw new IllegalArgumentException("Conflicting JSON paths");
            }
        }
        String leaf = parts[parts.length - 1];
        if (parent.has(leaf)) {
            throw new IllegalArgumentException("Conflicting JSON paths");
        }
        parent.add(leaf, value);
    }

    private static final class Work {
        final Object value;
        final JsonElement parent;
        final String name;

        Work(Object value, JsonElement parent, String name) {
            this.value = value;
            this.parent = parent;
            this.name = name;
        }
    }
}
