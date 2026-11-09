# Development setup

## Repository layout

| Location | Purpose |
|---|---|
| `pom.xml` | Shared Java parent and four-service Maven aggregator |
| `services/records-service/` | Record discovery, acquisition, preparation and source ownership |
| `services/search-service/` | Published issue, record and meeting search |
| `services/analysis-service/` | Evidence, developments, issues, linking and derived results |
| `services/pipeline-coordinator-service/` | Processing progression and recovery coordination |
| `services/ocr-service/` | Python OCR wrapper |
| `frontend/` | React, TypeScript and Vite frontend |
| `infra/` | Future local launch, model-serving and API-routing configuration |
| `docs/engineering/` | Maintained SRS, SPMP and ADRs |

The four Java modules contain independently runnable Spring Boot web applications and real-server startup tests. Records also has PostgreSQL-backed persistence and original-file storage through its application layer. Domain HTTP endpoints, portal acquisition, processing, OCR/model invocation and frontend scripts remain unimplemented.

## Java build

Use JDK 25, Spring Boot 4.1.2 and Maven 3.10.0. The root POM inherits Spring Boot's managed defaults and aggregates four Java modules. Each child declares the MVC starter, test starter and executable packaging plugin. The artifact version `0.1.0-SNAPSHOT` is code build metadata, independent of engineering-document versions.

With Maven available and `JAVA_HOME` configured for JDK 25:

```sh
mvn --version
mvn clean verify
```

`verify` compiles the modules, runs their tests and packages executable JARs. Records tests require a dedicated PostgreSQL database, separate migration/runtime credentials and a configured test storage root. With Docker running, the PowerShell helper provisions an isolated database, runs the full build and removes its own container:

```powershell
./tools/verify-java.ps1
```

The helper accepts `-MavenCommand` and `-MavenRepository` overrides. Its synthetic storage fixtures remain in ignored `.local/testing/`. The [records setup](RECORDS_PERSISTENCE.md) describes manual configuration. Database checks are not replaced with H2 or silently skipped. Each service also has a real-server startup test. There is no Maven Wrapper yet.

Run a packaged service after the build:

```sh
java -jar services/records-service/target/records-service-0.1.0-SNAPSHOT.jar
```

The other service JARs follow the same naming pattern. Each process receives an available port by default, which is reported in its startup log. Set `SERVER_PORT` or pass `--server.port=PORT` with an actual port number when a stable address is needed. Run the four processes in separate terminals or IntelliJ run configurations. Maven aggregation does not start them together. Stop each process when its local check is complete.

Records requires its configured database and storage root before startup. The other three Java services still start without a database, OCR service or model server. None exposes product routes yet. Their HTTP startup checks remain distinct from cross-service domain integration.

Open the root `pom.xml` in IntelliJ and import the Maven modules. Set the project SDK and Maven runner JDK to 25, and use Maven 3.10.0. Each service has its own application main class. Open `frontend/` in VS Code for frontend work once its package configuration is prepared. Personal IDE metadata stays out of Git.

## Conventions

Use the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for Java, including two-space block indentation and the guide's naming rules. The Maven group and base package are `org.civictracker`. Service packages use `records`, `search`, `analysis` and `coordinator`. Service folders and artifact IDs use lowercase hyphenated names. No formatter or enforcement plugin is added yet.

Do not depend on another service's implementation or JPA entities. Inter-service data access uses owner-controlled HTTP APIs with structured JSON and typed Java clients as contracts are defined. Maven aggregation controls builds; it is not runtime service discovery or a shared JVM.

Keep local datasets, private notes, archived prototypes and generated results in ignored `.local/`. Keep credentials and personal configuration untracked. Original-file locations and service configuration must be supplied through configuration, arguments or environment settings.

Framework references: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Boot Maven parent](https://docs.spring.io/spring-boot/maven-plugin/using.html), [Maven POM inheritance and aggregation](https://maven.apache.org/pom.html).
