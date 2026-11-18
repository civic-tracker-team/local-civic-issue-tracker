# Development setup

The Java backend uses Java 25, Spring Boot 4.1.2 and Maven 3.10.0. Each service is a separate Maven module and executable JAR. The OCR service has its own Python environment.

## Build and test

Set `JAVA_HOME` to JDK 25 and make Maven available. With Docker running, the verification script creates a temporary PostgreSQL database and runs the complete Java build:

```powershell
./tools/verify-java.ps1
```

The script accepts `-MavenCommand` and `-MavenRepository` when Maven or its cache is in a different location. It removes its test container afterward. Synthetic file fixtures stay in ignored local storage.

For a manually configured test database, run:

```sh
mvn clean verify
```

The records tests need a dedicated database, separate migration and runtime credentials, and a test storage directory. [Records persistence](RECORDS_PERSISTENCE.md) describes those settings. Tests must not target a shared development dataset.

## Run the services

After the build, start a service with its packaged JAR:

```sh
java -jar services/records-service/target/records-service-0.1.0-SNAPSHOT.jar
```

The other Java modules use the same naming pattern. An available port is selected by default and reported at startup. Set `SERVER_PORT` when a fixed port is needed. Start each service separately through a terminal or IDE run configuration. Maven builds the modules together but does not launch them together.

Records needs its database and storage configuration before startup. Internal requests also require configured credentials. The other Java modules currently provide startup baselines rather than complete domain APIs.

## Guides

| Guide | Contents |
|---|---|
| [Records persistence](RECORDS_PERSISTENCE.md) | Database roles, storage, identity and original versions |
| [Records reads](RECORDS_READS.md) | Metadata, exact-version reads and original downloads |
| [Records acquisition](RECORDS_ACQUISITION.md) | PrimeGov configuration, commands and recovery |
| [Record preparation](RECORDS_PREPARATION.md) | Native extraction, OCR and prepared-content reads |

The [design overview](../engineering/design/DESIGN_OVERVIEW.md) explains service ownership and processing. Architecture decisions are in `docs/engineering/adr/`.

## Development conventions

Java follows the Google Java Style Guide and uses the `org.civictracker` namespace. Services exchange DTOs through APIs rather than sharing JPA entities. Locations and credentials come from configuration, arguments or environment variables. Personal configuration, datasets and experiment results stay outside version control.
