#!/bin/bash
# SPDX-License-Identifier: EUPL-1.2
# Copyright (C) 2025 Gemeente Utrecht

# Script to undo Flyway migrations manually
# Since Flyway Community Edition doesn't support automatic undo,
# this script helps with manual undo operations

set -e

MIGRATION_VERSION=$1

if [ -z "$MIGRATION_VERSION" ]; then
    echo "Usage: ./flyway-undo.sh <version>"
    echo "Example: ./flyway-undo.sh 1"
    exit 1
fi

UNDO_FILE="src/main/resources/db/migration/U${MIGRATION_VERSION}__*.sql"

if ! ls $UNDO_FILE 1> /dev/null 2>&1; then
    echo "Error: No undo file found for version $MIGRATION_VERSION"
    echo "Expected file matching: $UNDO_FILE"
    exit 1
fi

echo "Found undo file: $(ls $UNDO_FILE)"
echo "Executing undo script..."

# Execute the undo script
docker exec -i dmf-postgres psql -U documenten -d documenten < $(ls $UNDO_FILE)

echo "Removing migration record from history..."
docker exec dmf-postgres psql -U documenten -d documenten -c "DELETE FROM flyway_schema_history WHERE version = '$MIGRATION_VERSION';"

echo "✓ Successfully undone migration version $MIGRATION_VERSION"
echo ""
echo "You can verify with: ./gradlew flywayInfo"
