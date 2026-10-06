# JSON Map serialization demo

Experimental use-case evidence for [#1833](https://github.com/ClickHouse/clickhouse-java/issues/1833#issuecomment-5996358565), not a production feature.

Branch: `feat/json-map-demo`. Base: `41a9d05db79e1804763309770e0cfabfca986d11` (`0.12.0-rc1-SNAPSHOT`). Compare with `feat/json-reader-demo` at the same base.

## What this demonstrates

```text
binary JSON cell → existing RowBinaryFormatReader → Java Map → configured Gson
```

The test reads real RowBinary bytes, not a fabricated Map. Gson 2.10.1 is already a dependency. The only adapter converts `BinaryStreamReader.ArrayValue` to a list; plain Gson throws `UnsupportedOperationException` on the array fixture. No path or Tuple repair is applied.

| Input structure | Map serialized with Gson | Structure-preserving result |
| --- | --- | --- |
| Flat fields | `{"a":1,"text":"z"}` | Same |
| Paths `a.x`, `a.y` | `{"a.x":1,"a.y":"z"}` | `{"a":{"x":1,"y":"z"}}` |
| Named Tuple at `a` | `{"a":[1,"z"]}` | `{"a":{"x":1,"y":"z"}}` |
| Different Tuple names | `{"a":[1,"z"]}` | `{"a":{"u":1,"v":"z"}}` |
| Array of named Tuples | `{"items":[[1,"z"],[2,"w"]]}` | `{"items":[{"x":1,"y":"z"},{"x":2,"y":"w"}]}` |

Thus valid JSON syntax alone does not establish equivalent structure. The two Dynamic Tuple fixtures produce indistinguishable Java values although their field names differ. This is a limitation of using the existing Java representation to reconstruct JSON, not a claim that returning `Object[]` for Tuple is itself a bug.

Ten shared cases also cover an explicitly typed Tuple, an array containing SQL NULL elements, an empty JSON object, a literal dotted Map key, and SQL NULL for the whole cell. Every case has columns before and after the JSON value to detect stream misalignment. SQL NULL remains Java `null`, not the text `"null"`.

## Memory and timing

This is a qualitative description, not a memory measurement. While serializing, the decoded Map/arrays coexist with the text writer's buffer. The `ArrayValue` adapter also builds a temporary Gson subtree through `context.serialize(...)` and caches the `asList()` result in the input wrapper; this demo is not entirely tree-free. The returned String needs storage too, and retaining both the Map and text retains both representations.

No byte totals, peak-heap measurements, allocation totals, or memory multipliers are claimed. These correctness tests are not a controlled throughput benchmark, and the two demos do not always produce equivalent structures. We therefore report no speed comparison.

## Run

Use the repository's Maven prerequisites and a configured JDK 17 toolchain. From the repository root:

```sh
TZ=UTC mvn -B -ntp -Dmaven.gitcommitid.nativegit=true -pl client-v2 -am \
  -Dtest=JsonMapDemoTest -Dsurefire.failIfNoSpecifiedTests=false -DskipITs package
```

The targeted test class has 11 invocations. For the unit reactor, omit the two test-selection properties. `package` supplies the dependency JARs needed by the multi-release build; the native-Git property supports Git worktrees. No integration tests or live JDBC connection are involved.

## Fixture provenance and limits

`JsonDemoFixtures` contains hand-constructed binary cells, not a network capture. The nine non-null cells were independently accepted by official ClickHouse 25.8.13.73 with the structures shown in the companion SQL file:

```sh
clickhouse local --multiquery --queries-file \
  client-v2/src/test/resources/jsondemo/verify-fixtures.txt --format TSVRaw
```

`toJSONString` is used there only as an independent structural oracle. Neither Java test enables `output_format_binary_write_json_as_string`. The whole-cell Nullable(JSON) NULL case is Java-side only; the SQL file does not assert support for it on that server version.

This branch changes only test files and this document. It does not change `getObject()`, `getString()`, production readers, API selection, or server settings. Escaped JSON paths, repeated Map keys and broader ClickHouse type coverage need a separate specification.
