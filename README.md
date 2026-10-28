# demo-scalardb-samples

A Spring Boot application on ScalarDB with:

- **CRUD** for four tables (`customer.customers`, `inventory.items`, `ordering.orders`,
  `ordering.statements`), each with a Java API version and a SQL API version
- **Business logic** from scalardb-sample: PlaceOrder, GetOrder, GetOrders, plus CancelOrder
  (Java API: `/orders`, SQL API: `/orders-sql`)
- **Load tests** (simple benchmarks) that run inside the application:
  `place-order` and `customer-crud` (W1-W4). See [docs/load-test-design.md](docs/load-test-design.md).

The Java API can run on **ScalarDB Cluster** (default) or directly on **ScalarDB Core**
(profile `core`). The SQL API always uses ScalarDB Cluster.

## Prerequisites

- JDK 25 (the Gradle toolchain uses Java 25)
- PostgreSQL (the backend database)
- ScalarDB Cluster 3.19.1, to run on Cluster (the license is set on the Cluster side)
- To create the tables, one of:
  - ScalarDB Cluster SQL CLI (`scalardb-cluster-sql-cli-3.19.1-all.jar`)
  - ScalarDB Cluster Schema Loader (for Cluster)
  - ScalarDB Schema Loader (`scalardb-schema-loader-3.19.1.jar`, for Core)

## 1. Clone and build

```bash
git clone https://github.com/yu2scalar/demo-scalardb-samples.git
cd demo-scalardb-samples
./gradlew build -x test
ls build/libs/demo-scalardb-samples-0.0.1-SNAPSHOT.jar
```

Rebuild the jar after changing the source.

## 2. Configuration

The settings contain passwords, so the real files are not in git (`.gitignore`). Create
them from the examples, then edit them:

```bash
cp scalardb.properties.example scalardb.properties
cp scalardb_sql.properties.example scalardb_sql.properties
cp scalardb-core.properties.example scalardb-core.properties
```

The application reads these files from the **working directory** (the repository root):

| File | Used by | Connects to |
|---|---|---|
| `scalardb.properties` | Java API (default) | ScalarDB Cluster |
| `scalardb_sql.properties` | SQL API | ScalarDB Cluster |
| `scalardb-core.properties` | Java API with profile `core` | The database directly (ScalarDB Core, Consensus Commit) |

Edit them for your environment (replace every `<user>` and `<password>`; the host names in
the examples are only examples):

- `scalardb.properties` / `scalardb_sql.properties`: `contact_points` of the Cluster
  (`indirect:<host>`), and the user name and password of the Cluster (when
  `scalar.db.cluster.auth.enabled=true`)
- `scalardb-core.properties`: the JDBC URL, user name and password of the database, and
  `scalar.db.consensus_commit.isolation_level` (`READ_COMMITTED` in the example)

`scalardb-core.properties` must also have cross-partition scan with filtering enabled
(`scalar.db.cross_partition_scan.enabled=true` and
`scalar.db.cross_partition_scan.filtering.enabled=true`). The name search and the
`customer-crud` load test use ScanAll with a where condition. On Cluster, the same
settings are needed on the Cluster side.

`src/main/resources/application.properties` holds the retry settings (`retry.*`).
`application-core.properties` switches the Java API to `scalardb-core.properties`.

## 3. Create the tables

The schema is in `schema/schema.json` (for Schema Loader) and `schema/schema.sql` (for the SQL CLI).
Both define the same tables, including the secondary indexes (`name` of customers and items).

On ScalarDB Cluster, either:

```bash
# SQL CLI
java -jar scalardb-cluster-sql-cli-3.19.1-all.jar -c scalardb_sql.properties -f schema/schema.sql

# or Cluster Schema Loader
java -jar <cluster-schema-loader>.jar --config scalardb.properties -f schema/schema.json --coordinator
```

On ScalarDB Core:

```bash
java -jar scalardb-schema-loader-3.19.1.jar --config scalardb-core.properties -f schema/schema.json --coordinator
```

If the tables already exist and only columns or indexes were added to the schema, use
`--alter` (`-A`) instead of `--coordinator`.

Core and Cluster can use the same database. Create the tables only once.

## 4. Run

Run from the repository root (the properties files are read from the working directory).
`-Xmx1g` keeps the memory use small; change it as needed.

```bash
# Java API on ScalarDB Cluster (default)
java -Xmx1g -jar build/libs/demo-scalardb-samples-0.0.1-SNAPSHOT.jar

# Java API on ScalarDB Core
java -Xmx1g -jar build/libs/demo-scalardb-samples-0.0.1-SNAPSHOT.jar --spring.profiles.active=core
```

Options:

- Port: `--server.port=18090` (default 8080)
- Profile from the environment instead: `SPRING_PROFILES_ACTIVE=core`
- `./gradlew bootRun --args='--spring.profiles.active=core'` also works, but it runs Gradle
  as well and uses more memory.

To run in the background and stop it later:

```bash
setsid nohup java -Xmx1g -jar build/libs/demo-scalardb-samples-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=core > app.log 2>&1 < /dev/null &
grep -m1 'Started' app.log     # the line shows the process ID
kill <process ID>
```

In an IDE, run `com.example.demo_scalardb_samples.DemoScalardbSamplesApplication` with the
repository root as the working directory, and `--spring.profiles.active=core` as a program
argument for Core.

## 5. Check that it works

- The log shows `The following 1 profile is active: "core"` when running on Core
  (nothing about profiles on Cluster).
- Swagger UI: http://localhost:8080/swagger-ui.html

```bash
# Initial data: customers 1..10, items 1..10 (upsert; truncate=true empties the 4 tables first)
curl -s -X POST 'http://localhost:8080/init?customers=10&items=10'

# PlaceOrder and GetOrder
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"customer_id":1,"items":[{"item_id":1,"qty":2}]}'
curl -s http://localhost:8080/orders/<order_id>

# Customers by name (exact, or prefix with match=prefix)
curl -s 'http://localhost:8080/customer-customers/search?name=TEST-000001'

# Available load-test scenarios
curl -s http://localhost:8080/load-test/scenarios
```

## 6. Load tests

Each scenario has `init` (prepares the data) and `run` (runs on several threads and returns
throughput, latency, a per-second timeline and a database check). Details, parameters and
how to read the results: [docs/load-test-design.md](docs/load-test-design.md).

**Run one load test at a time, with no other load.** `init` truncates tables, and the
database check assumes that nothing else writes to them. When Core and Cluster use the same
database, do not run load tests on both at the same time.

### place-order

```bash
B=http://localhost:8080/load-test/place-order
curl -s -X POST $B/init -H 'Content-Type: application/json' \
  -d '{"params":{"customers":1000,"items":1000,"truncate":true}}'
curl -s -X POST $B/run -H 'Content-Type: application/json' \
  -d '{"number_of_threads":8,"ramp_up_period_seconds":5,"duration_seconds":30,
       "params":{"items_per_order_max":3,"qty_max":5}}'
```

### customer-crud (W1-W4)

On `customer.customers` only. Run W1 first: the rows it inserts are used by W2-W4.

`init` returns at once: it only truncates `customer.customers` and remembers `record_count`.
The rows are inserted by the W1 run (so that the insert itself is measured), which waits
until all of them are in.

```bash
C=http://localhost:8080/load-test/customer-crud
curl -s -X POST $C/init -H 'Content-Type: application/json' -d '{"params":{"record_count":100000}}'

# W1: Insert-only (stops when all rows are in; duration_seconds is only an upper limit)
curl -s -X POST $C/run -H 'Content-Type: application/json' \
  -d '{"number_of_threads":8,"ramp_up_period_seconds":5,"duration_seconds":3600,"params":{"workload":"insert"}}'

# W2: 50% Read / 50% Update (read-modify-write)
curl -s -X POST $C/run -H 'Content-Type: application/json' \
  -d '{"number_of_threads":8,"ramp_up_period_seconds":10,"duration_seconds":60,
       "params":{"workload":"rmw","read_ratio":50,"match":"exact"}}'

# W3: 95% Read / 5% Update
curl -s -X POST $C/run -H 'Content-Type: application/json' \
  -d '{"number_of_threads":8,"ramp_up_period_seconds":10,"duration_seconds":60,
       "params":{"workload":"rmw","read_ratio":95,"match":"exact"}}'

# W4: 100% Read
curl -s -X POST $C/run -H 'Content-Type: application/json' \
  -d '{"number_of_threads":8,"ramp_up_period_seconds":10,"duration_seconds":60,
       "params":{"workload":"read","match":"exact"}}'
```

- `"match":"prefix"` searches with `name LIKE 'TEST-00123%'` instead (`prefix_digits`, default 1).
- `"warmup_seconds"` adds a period after the ramp-up that runs on all threads but is not counted.
- After restarting the application, `init` with `"truncate":false` remembers the row count
  without deleting the rows, so W2-W4 can run without W1.

## Notes

- `POST /init` upserts. Running it twice does not fail; it overwrites the rows. Orders are
  kept unless `truncate=true`, so `credit_total` and `stock` may no longer match them.
- Each failed transaction attempt is logged at ERROR with a stack trace.
- On ScalarDB (Consensus Commit), a secondary index on a column also creates one on its
  before-image column (`before_<column>`), and a read by that column also checks the
  before-image. See [docs/load-test-design.md](docs/load-test-design.md).
