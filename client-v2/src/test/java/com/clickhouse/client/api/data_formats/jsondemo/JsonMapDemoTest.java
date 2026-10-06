package com.clickhouse.client.api.data_formats.jsondemo;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

public class JsonMapDemoTest {
    @DataProvider(name = "jsonCases")
    public Object[][] jsonCases() {
        return JsonDemoFixtures.cases();
    }

    @Test(dataProvider = "jsonCases")
    public void characterizeMapSerialization(String name, String type, String hex,
                                             String expectedMap, String expectedStructured) throws Exception {
        Map<String, Object> row = JsonDemoFixtures.defaultRow(type, JsonDemoFixtures.row(hex));
        Object value = row.get("j");
        if (expectedMap == null) {
            Assert.assertNull(value);
        } else {
            Assert.assertTrue(value instanceof Map);
            String text = JsonDemoFixtures.GSON.toJson(value);
            Assert.assertEquals(JsonParser.parseString(text), JsonParser.parseString(expectedMap));
            System.out.println(name + ": map=" + text + "; structured=" + expectedStructured);
        }
    }

    @Test
    public void plainGsonCannotSerializeArrayValue() throws Exception {
        Object value = JsonDemoFixtures.defaultRow("JSON",
                JsonDemoFixtures.row("01056974656d731e2309030001000000010002000000")).get("j");
        Assert.expectThrows(UnsupportedOperationException.class, () -> new Gson().toJson(value));
    }
}
