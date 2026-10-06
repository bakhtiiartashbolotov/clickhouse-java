# JSON metadata-capture reader demo

Experimental use-case evidence for [#1833](https://github.com/ClickHouse/clickhouse-java/issues/1833#issuecomment-5996358565), not a production feature.

Branch: `feat/json-reader-demo`. Base: `41a9d05db79e1804763309770e0cfabfca986d11` (`0.12.0-rc1-SNAPSHOT`). Compare with `feat/json-map-demo` at the same base.

## What this demonstrates

```text
binary JSON cell → existing decoder + temporary Tuple names → Gson tree → JSON text
```

`JsonTreeDemoReader` is a test-only subclass. It reuses the existing binary decoder, captures Tuple names before their description is discarded, then groups JSON paths and builds `JsonObject` / `JsonArray` nodes. It does not implement a separate direct-to-tree decoder: the intermediate Java Map, arrays and `ArrayValue` wrappers still exist during conversion. It is not a proposed production architecture.

| Input structure | Map branch | This demo |
| --- | --- | --- |
| Paths `a.x`, `a.y` | `{"a.x":1,"a.y":"z"}` | `{"a":{"x":1,"y":"z"}}` |
| Named Tuple at `a` | `{"a":[1,"z"]}` | `{"a":{"x":1,"y":"z"}}` |
| Different Tuple names | `{"a":[1,"z"]}` | `{"a":{"u":1,"v":"z"}}` |
| Array of named Tuples | `{"items":[[1,"z"],[2,"w"]]}` | `{"items":[{"x":1,"y":"z"},{"x":2,"y":"w"}]}` |

The ten shared cases additionally cover flat JSON, a declared Tuple path, nullable array elements, a literal dotted Map key, empty JSON, and whole-cell SQL NULL. Tuple names and Map tags are cleared after each cell; returned trees own their nodes and survive later reads. SQL NULL remains Java `null`.

Six boundary tests check rejection of non-JSON columns without consuming bytes, non-finite floating-point values, non-string Map keys, scalar/path conflicts, object-leaf/path conflicts, and independence of successive rows including SQL NULL. Unsupported cases are not silently presented as equivalent JSON.

## Memory and timing

This is a qualitative description, not a memory measurement. During conversion, the decoded Map/arrays, captured Tuple names and Map tags, sorted path index, work queue, and growing Gson tree can coexist. The tree adds containers and primitive wrappers, but Gson's String/Number primitives reference the supplied values rather than copying all scalar payloads. This is not necessarily two complete copies of the data.

`finally` clears the captured references after each cell. The identity maps' backing capacity can remain allocated while the reader is alive; `clear()` does not shrink it. The caller retains the returned tree and its scalar values. Creating and retaining JSON text afterwards adds String storage; the helper does not cache a completed JSON string.

The reader separately retains its input, buffers, and last type descriptor. With these in-memory fixtures, retaining the reader/input also retains the input byte array; that is not storage owned by the returned tree.

No byte totals, peak-heap measurements, allocation totals, or memory multipliers are claimed. These correctness tests are not a controlled throughput benchmark, and the two demos do not always produce equivalent structures. We therefore report no speed comparison.

## Run

Use the repository's Maven prerequisites and a configured JDK 17 toolchain. From the repository root:

```sh
TZ=UTC mvn -B -ntp -Dmaven.gitcommitid.nativegit=true -pl client-v2 -am \
  -Dtest=JsonReaderDemoTest -Dsurefire.failIfNoSpecifiedTests=false -DskipITs package
```

The targeted test class has 16 invocations. For the unit reactor, omit the two test-selection properties. `package` supplies the dependency JARs needed by the multi-release build; the native-Git property supports Git worktrees. No integration tests or live JDBC connection are involved.

## Fixture provenance and limits

`JsonDemoFixtures` contains hand-constructed binary cells, not a network capture. The nine non-null cells were independently accepted by official ClickHouse 25.8.13.73 with the structures shown in the companion SQL file:

```sh
clickhouse local --multiquery --queries-file \
  client-v2/src/test/resources/jsondemo/verify-fixtures.txt --format TSVRaw
```

`toJSONString` is used there only as an independent structural oracle. Neither Java test enables `output_format_binary_write_json_as_string`. The whole-cell Nullable(JSON) NULL case is Java-side only; the SQL file does not assert support for it on that server version.

This branch changes only test files and this document. It does not wire a reader into JDBC or change `getObject()`, `getString()`, production readers, public APIs, or server settings.

Not covered: escaped JSON path segments, repeated Map keys already collapsed by the default decoder, comprehensive ClickHouse scalar semantics, nested typed JSON values, or extreme depth/size. Non-empty nested JSON and non-string Map keys are explicitly rejected. A direct-to-tree reader would need an approved specification and separate memory measurements.
