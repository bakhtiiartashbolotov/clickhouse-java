package com.clickhouse.client.api.data_formats.jsondemo;

import com.clickhouse.client.api.data_formats.RowBinaryFormatReader;
import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;

import org.testng.Assert;

final class JsonDemoFixtures {
    private JsonDemoFixtures() {
    }

    static final Gson GSON = new GsonBuilder().serializeNulls()
            .registerTypeAdapter(BinaryStreamReader.ArrayValue.class,
                    (JsonSerializer<BinaryStreamReader.ArrayValue>) (value, type, context) ->
                            context.serialize(value.asList()))
            .create();

    static Object[][] cases() {
        return new Object[][] {
                {"flat", "JSON", "0201610901000000047465787415017a",
                        "{\"a\":1,\"text\":\"z\"}", "{\"a\":1,\"text\":\"z\"}"},
                {"paths", "JSON", "0203612e78090100000003612e7915017a",
                        "{\"a.x\":1,\"a.y\":\"z\"}", "{\"a\":{\"x\":1,\"y\":\"z\"}}"},
                {"declared-tuple", "JSON(a Tuple(x Int32, y String))", "01016101000000017a",
                        "{\"a\":[1,\"z\"]}", "{\"a\":{\"x\":1,\"y\":\"z\"}}"},
                {"dynamic-xy", "JSON", "010161200201780901791501000000017a",
                        "{\"a\":[1,\"z\"]}", "{\"a\":{\"x\":1,\"y\":\"z\"}}"},
                {"dynamic-uv", "JSON", "010161200201750901761501000000017a",
                        "{\"a\":[1,\"z\"]}", "{\"a\":{\"u\":1,\"v\":\"z\"}}"},
                {"nullable-array", "JSON", "01056974656d731e2309030001000000010002000000",
                        "{\"items\":[1,null,2]}", "{\"items\":[1,null,2]}"},
                {"tuple-array", "JSON", "01056974656d731e20020178090179150201000000017a020000000177",
                        "{\"items\":[[1,\"z\"],[2,\"w\"]]}",
                        "{\"items\":[{\"x\":1,\"y\":\"z\"},{\"x\":2,\"y\":\"w\"}]}"},
                {"literal-map-key", "JSON(m Map(String, Int32))", "01016d0103612e6201000000",
                        "{\"m\":{\"a.b\":1}}", "{\"m\":{\"a.b\":1}}"},
                {"empty", "JSON", "00", "{}", "{}"},
                {"sql-null", "Nullable(JSON)", "01", null, null}
        };
    }

    static ByteArrayInputStream row(String cellHex) {
        return new ByteArrayInputStream(bytes("07" + cellHex + "2a000000"));
    }

    static Map<String, Object> defaultRow(String type, ByteArrayInputStream input) throws Exception {
        TableSchema schema = new TableSchema(Arrays.asList(
                ClickHouseColumn.of("before", "UInt8"),
                ClickHouseColumn.of("j", type),
                ClickHouseColumn.of("after", "Int32")));
        try (RowBinaryFormatReader reader = new RowBinaryFormatReader(input,
                new QuerySettings().setUseTimeZone("UTC"), schema,
                new BinaryStreamReader.DefaultByteBufferAllocator())) {
            Map<String, Object> row = reader.next();
            Assert.assertNotNull(row);
            Assert.assertEquals(row.get("before"), (short) 7);
            Assert.assertEquals(row.get("after"), 42);
            Assert.assertEquals(input.available(), 0);
            Assert.assertNull(reader.next());
            return row;
        }
    }

    static byte[] bytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
