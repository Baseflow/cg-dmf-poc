# Handleiding Databasemigraties

## Tooling

- **Migratietool:** Flyway (Community Edition)
- **ORM:** Exposed 1.3.0
- **Database:** PostgreSQL 16
- **Migratiegenerator:** `exposed-migration-jdbc`

## Overzicht commando's

### Commando's

```bash
# Controleer migratiestatus
./gradlew flywayInfo

# Pas openstaande migraties toe
./gradlew flywayMigrate

# Valideer migraties
./gradlew flywayValidate

# Maak de laatste migratie ongedaan
./gradlew flywayUndo

# Maak een specifieke versie ongedaan
./gradlew flywayUndo -Pargs=<versie>

# Genereer migratie van Exposed-modellen
./gradlew generateMigration -Pargs="V2__Description"
```

### Omgevingsvariabelen

- `DB_URL` (standaard: `jdbc:postgresql://localhost:5432/documenten`)
- `DB_USER` (standaard: `documenten`)
- `DB_PASSWORD` (standaard: `documenten`)

## Migraties aanmaken

### Workflow

1. **Pas de Exposed-tabeldefinitie aan**
   ```kotlin
   // src/main/kotlin/entities/EIORecord.kt
   object EIORecords : UUIDTable("eio_records") {
       val title = varchar("title", 255).nullable()
   }
   ```

2. **Genereer de migratie**
   ```bash
   ./gradlew generateMigration -Pargs="V2__Add_title_column"
   ```

3. **Controleer de gegenereerde SQL**
   ```bash
   cat src/main/resources/db/migration/V2__Add_title_column.sql
   ```
   **Belangrijk:** Controleer en test altijd het gegenereerde script.

4. **Maak een undo-script aan**

   Hier is helaas geen generator voor beschikbaar
   ```sql
   -- src/main/resources/db/migration/U2__Drop_title_column.sql
   ALTER TABLE eio_records DROP COLUMN title;
   ```

5. **Test de migratie**
   ```bash
   ./gradlew flywayMigrate
   ./gradlew flywayInfo
   ```

6. **Test het ongedaan maken**
   ```bash
   ./gradlew flywayUndo -Pargs=2
   ./gradlew flywayInfo
   ```

### Bestandsnaamconventie

| Prefix | Gebruik                                                   | Voorbeeld                 |
|--------|-----------------------------------------------------------|---------------------------|
| `V`    | Versioned upgrade — normale schemawijziging               | `V2__Add_user_table.sql`  |
| `U`    | Undo — maakt de bijbehorende versie ongedaan              | `U2__Drop_user_table.sql` |
| `B`    | Baseline — volledige schemadefinitie op een rollup-versie | `B10__Baseline_v10.sql`   |

### Baselines en rollups

Na verloop van tijd groeit het aantal migratiebestanden. Met een **baseline rollup** consolideer je alle voorgaande migraties tot één enkel script dat het volledige schema beschrijft op een bepaald versienummer. Flyway past de baseline-migratie toe op een lege database in plaats van alle eerdere versies opnieuw te doorlopen.

**Wanneer te gebruiken:** als het aantal migratiebestanden onhandelbaar wordt, of bij een major release waarbij oude undo-scripts toch niet meer relevant zijn.

**Workflow voor een rollup:**

1. **Kies een rollup-versienummer** — typisch een rond getal dat hoger ligt dan de huidige versie, bijv. `B10` als de laatste migratie `V9` is.

2. **Genereer het volledige schema** — dump het huidige schema uit een bijgewerkte database:
   ```bash
   pg_dump --schema-only --no-owner --no-acl -d documenten > B10__Baseline_v10.sql
   ```

3. **Bewerk het script** — verwijder `CREATE EXTENSION`, `SET`-statements en andere databasespecifieke configuratie die niet in de migratie thuishoren. Voeg de Flyway-schematabel niet op.

4. **Verwijder de geconsolideerde V- en U-bestanden** — bewaar de bestanden in de git-history, maar verwijder ze uit `src/main/resources/db/migration/`. Flyway slaat versies over die lager zijn dan het baseline-versienummer.

5. **Pas de baseline toe op bestaande omgevingen:**
   ```bash
   ./gradlew flywayBaseline -Pargs=10
   ```
   Dit markeert versie 10 als startpunt in de `flyway_schema_history`-tabel. Flyway voert `B10` dan niet opnieuw uit op databases die al bijgewerkt zijn.

6. **Test op een lege database** — verifieer dat `./gradlew flywayMigrate` op een lege database het volledige schema correct aanmaakt.

> Let op: na een rollup zijn de bijbehorende undo-scripts van de geconsolideerde versies niet meer bruikbaar. Zorg dat alle omgevingen up-to-date zijn vóór je de rollup uitvoert.

## Probleemoplossing

### Migratie toont SUCCESS maar tabellen bestaan niet

Voer de migratie opnieuw uit:
```bash
./gradlew flywayMigrate
```

### Database resetten

```bash
# Stop containers
docker-compose down

# Verwijder volume
docker volume rm dmf-poc_postgres_data

# Start opnieuw
docker-compose up -d postgres

# Pas migraties toe
./gradlew flywayMigrate
```

## Geavanceerd gebruik

### Migraties in Docker of CI

De klasse `FlywayMigration.kt` in `src/main/kotlin/shared/tooling/` wordt ook direct uitgevoerd in omgevingen waar Gradle niet beschikbaar is, zoals Docker-containers of CI-pipelines. Gebruik dan de `java`-aanroep rechtstreeks op de gepackagede jar:

```bash
# In een draaiende container
docker exec dmf-app java -cp /app/app.jar com.baseflow.shared.tooling.FlywayMigrationKt migrate

# Beschikbare subcommando's: migrate | info | validate | undo | repair | clean
```

De Gradle-taken (`./gradlew flywayMigrate` etc.) zijn wrappers hieromheen en zijn de aanbevolen manier voor lokale ontwikkeling.

## Gerelateerde bestanden

- Migratiegenerator: `src/main/kotlin/tooling/MigrationGenerator.kt`
- Flyway-runner: `src/main/kotlin/tooling/FlywayMigration.kt`
- Entiteitsdefinities: `src/main/kotlin/entities/`
- Migratiescripts: `src/main/resources/db/migration/`
