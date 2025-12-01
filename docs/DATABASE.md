# Database Migration Guide

## Tooling

- **Migration Tool:** Flyway (Community Edition)
- **ORM:** Exposed 1.0.0-rc-4
- **Database:** PostgreSQL 16
- **Migration Generator:** `exposed-migration-jdbc`

## Quick Reference

### Commands

```bash
# Check migration status
./gradlew flywayInfo

# Apply pending migrations
./gradlew flywayMigrate

# Validate migrations
./gradlew flywayValidate

# Undo a migration (manual)
./flyway-undo.sh <version>

# Generate migration from Exposed models
./gradlew generateMigration -Pargs="V2__Description"
```

### Environment Variables

- `DB_URL` (default: `jdbc:postgresql://localhost:5432/documenten`)
- `DB_USER` (default: `documenten`)
- `DB_PASSWORD` (default: `documenten`)

## Creating Migrations

### Workflow

1. **Update Exposed Table Definition**
   ```kotlin
   // src/main/kotlin/entities/EIORecord.kt
   object EIORecords : UUIDTable("eio_records") {
       val title = varchar("title", 255).nullable()
   }
   ```

2. **Generate Migration Script**
   ```bash
   ./gradlew generateMigration -Pargs="V2__Add_title_column"
   ```

3. **Review Generated SQL**
   ```bash
   cat src/main/resources/db/migration/V2__Add_title_column.sql
   ```
   **Important:** Always review and test. The generator is pretty new and may not detect all changes to the schema.

4. **Create Undo Script** (manual)
   ```sql
   -- src/main/resources/db/migration/U2__Drop_title_column.sql
   ALTER TABLE eio_records DROP COLUMN title;
   ```

5. **Test Migration**
   ```bash
   ./gradlew flywayMigrate
   ./gradlew flywayInfo
   ```

6. **Test Undo**
   ```bash
   ./flyway-undo.sh 2
   ./gradlew flywayInfo
   ```

### File Naming Convention

- **Upgrade:** `V<version>__<Description>.sql` (e.g., `V2__Add_user_table.sql`)
- **Undo:** `U<version>__<Description>.sql` (e.g., `U2__Drop_user_table.sql`)

## Troubleshooting

### Migration Shows SUCCESS but Tables Don't Exist

Re-run the migration:
```bash
./gradlew flywayMigrate
```

### Need to Reset Database

```bash
# Stop containers
docker-compose down

# Remove volume
docker volume rm dmf-poc_postgres_data

# Start fresh
docker-compose up -d postgres

# Apply migrations
./gradlew flywayMigrate
```

## Advanced Usage

### Programmatic Migrations

The `FlywayMigration.kt` class can be used directly:

```bash
./gradlew run --args='migrate'
./gradlew run --args='info'
./gradlew run --args='validate'
```

### Custom Migration Logic

For complex migrations that need data transformation:

1. Create standard `V*.sql` file with schema changes
2. Add Kotlin code in transaction if needed
3. Keep data transformations in separate scripts

## Related Files

- Migration generator: `src/main/kotlin/tooling/MigrationGenerator.kt`
- Flyway runner: `src/main/kotlin/tooling/FlywayMigration.kt`
- Undo helper: `flyway-undo.sh`
- Entity definitions: `src/main/kotlin/entities/`
- Migration scripts: `src/main/resources/db/migration/`

