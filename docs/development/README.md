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

This is a structural scaffold. There are no runnable service entry points, application dependencies, database schemas, endpoints or frontend scripts yet. Empty source directories are retained with `.gitkeep` files.

## Java build

Use JDK 25, Spring Boot 4.1.2 and Maven 3.10.0. The root POM inherits Spring Boot's managed build/dependency defaults and aggregates four Java modules. Child POMs inherit the project coordinates and Java version. They intentionally declare no application dependencies yet. The initial artifact version `0.1.0-SNAPSHOT` describes code build metadata and is independent of SRS version 1.0.

With Maven available and `JAVA_HOME` configured for JDK 25:

```sh
mvn --version
mvn validate
```

`validate` checks the Maven project configuration. It does not demonstrate a running service, compile business code or execute product tests. There is no Maven Wrapper yet. Do not use `spring-boot:run` until service dependencies and entry points are added.

Open the root `pom.xml` in IntelliJ and import the Maven modules. Set the project SDK and Maven runner JDK to 25, and use Maven 3.10.0. Open `frontend/` in VS Code for frontend work once its package configuration is prepared. Personal IDE metadata stays out of Git.

## Conventions

Use the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for Java, including two-space block indentation and the guide's naming rules. The Maven group and base package are `org.civictracker`. Service packages use `records`, `search`, `analysis` and `coordinator`. Service folders and artifact IDs use lowercase hyphenated names. No formatter or enforcement plugin is added yet.

Do not depend on another service's implementation or JPA entities. Inter-service data access uses owner-controlled HTTP APIs with structured JSON and typed Java clients as contracts are defined. Maven aggregation controls builds; it is not runtime service discovery or a shared JVM.

Keep local datasets, private notes, archived prototypes and generated results in ignored `.local/`. Keep credentials and personal configuration untracked. Original-file locations and service configuration must be supplied through configuration, arguments or environment settings.

Framework references: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Boot Maven parent](https://docs.spring.io/spring-boot/maven-plugin/using.html), [Maven POM inheritance and aggregation](https://maven.apache.org/pom.html).
