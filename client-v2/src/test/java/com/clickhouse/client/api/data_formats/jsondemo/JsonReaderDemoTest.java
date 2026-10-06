package com.clickhouse.client.api.data_formats.jsondemo;

import com.clickhouse.data.ClickHouseColumn;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;

public class JsonReaderDemoTest {
    @DataProvider(name = "jsonCases")
    public Object[][] jsonCases() {
        return JsonDemoFixtures.cases();
    }

    @Test(dataProvider = "jsonCases")
    public void preserveJsonStructure(String name, String type, String hex,
                                      String expectedMap, String expectedStructured) throws Exception {
        ByteArrayInputStream input = JsonDemoFixtures.row(hex);
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.assertEquals((Object) reader.readValue(ClickHouseColumn.of("before", "UInt8")), (short) 7);
        JsonElement actual = reader.readJson(ClickHouseColumn.of("j", type));
        Assert.assertEquals((Object) reader.readValue(ClickHouseColumn.of("after", "Int32")), 42);
        Assert.assertEquals(input.available(), 0);
        if (expectedStructured == null) {
            Assert.assertNull(actual);
        } else {
            Assert.assertEquals(actual, JsonParser.parseString(expectedStructured), name);
            System.out.println(name + ": tree=" + actual);
        }
    }

    @Test
    public void rejectNonJsonColumnWithoutConsumingBytes() {
        ByteArrayInputStream input = new ByteArrayInputStream(JsonDemoFixtures.bytes("017a"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> reader.readJson(ClickHouseColumn.of("j", "String")));
        Assert.assertEquals(input.available(), 2);
    }

    @Test
    public void rejectNonFiniteNumbers() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                JsonDemoFixtures.bytes("01016e000000000000f07f"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> reader.readJson(ClickHouseColumn.of("j", "JSON(n Float64)")));
    }

    @Test
    public void rejectConflictingPaths() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                JsonDemoFixtures.bytes("020161090100000003612e620902000000"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> reader.readJson(ClickHouseColumn.of("j", "JSON")));
    }

    @Test
    public void rejectObjectLeafAndChildPathConflict() {
        ByteArrayInputStream input = new ByteArrayInputStream(JsonDemoFixtures.bytes(
                "020161200201780901791501000000017a03612e620902000000"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> reader.readJson(ClickHouseColumn.of("j", "JSON")));
    }

    @Test
    public void rejectNonStringMapKeys() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                JsonDemoFixtures.bytes("01016d0107000000017a"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> reader.readJson(ClickHouseColumn.of("j", "JSON(m Map(UInt32, String))")));
    }

    @Test
    public void returnedTreeSurvivesNextRowAndNull() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(JsonDemoFixtures.bytes(
                "00010161200201780901791501000000017a"
                        + "00010161200201750901761501000000017a01"));
        JsonTreeDemoReader reader = new JsonTreeDemoReader(input);
        ClickHouseColumn column = ClickHouseColumn.of("j", "Nullable(JSON)");
        JsonElement first = reader.readJson(column);
        JsonElement second = reader.readJson(column);
        Assert.assertNull(reader.readJson(column));
        Assert.assertEquals(first, JsonParser.parseString("{\"a\":{\"x\":1,\"y\":\"z\"}}"));
        Assert.assertEquals(second, JsonParser.parseString("{\"a\":{\"u\":1,\"v\":\"z\"}}"));
        Assert.assertEquals(input.available(), 0);
    }
}
