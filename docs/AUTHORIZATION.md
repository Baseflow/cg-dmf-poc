# JWT Scope-Based Authorization

This document describes how to implement scope-based authorization in the application using JWT tokens.

## Overview

The application implements OAuth2/OIDC scope-based authorization, allowing you to protect routes based on JWT scopes. Users must have the required scopes in their JWT token to access protected routes.

The authorization is implemented using a route-level approach where scopes are checked after authentication completes, ensuring the JWT principal is available.

## Features

- **Scope checking**: Verify users have required scopes from their JWT token
- **Flexible scope definition**: Use the `withScopes()` DSL for route protection
- **Wildcard matching**: Support for wildcard scopes (e.g., `documenten:*` matches `documenten:read`)
- **Multiple scope support**: Require multiple scopes with AND logic
- **Standard compliant**: Follows OAuth2/OIDC standards for scope claims

## Configuration

The `ScopeAuthorizationPlugin` is configured in `DocumentenApiRoutes.kt`:

```kotlin
install(ScopeAuthorizationPlugin) {
    scopeClaimName = "scope" // OAuth2 standard claim name
    wildcardEnabled = true   // Enable wildcard scope matching
}
```

### Configuration Options

- `scopeClaimName`: The JWT claim name containing scopes (default: `"scope"`)
  - Supports space-separated string (OAuth2 standard): `"documenten:read documenten:write"`
  - Supports array format: `["documenten:read", "documenten:write"]`
  - Automatically checks both `"scope"` and `"scopes"` claims

- `wildcardEnabled`: Enable wildcard matching (default: `true`)
  - `"documenten:*"` matches `"documenten:read"`, `"documenten:write"`, etc.

## Usage Examples

### Method 1: Using `withScopes()` DSL (Recommended)

Wrap route groups with required scopes:

```kotlin
fun Route.auditTrailRoutes() {
    val service: AuditTrailService by inject()

    // All routes within this block require "audittrail:read" scope
    withScopes("audittrail:read") {
        route("/{uuid}/audittrail/{auditTrailUuid}") {
            get {
                // Route handler
            }
        }

        route("/{uuid}/audittrail") {
            get {
                // Route handler
            }
        }
    }
}
```

### Method 2: Multiple Scopes (AND Logic)

Require multiple scopes (user must have ALL of them):

```kotlin
// User needs both "documenten:read" AND "documenten:admin"
withScopes("documenten:read", "documenten:admin") {
    delete("/{uuid}") {
        // Only accessible to users with both scopes
    }
}
```

### Method 3: Different Scopes per Route

Apply different scope requirements to different routes:

```kotlin
fun Route.documentRoutes() {
    // Read operations require "documenten:read"
    withScopes("documenten:read") {
        get {
            // List documents
        }
        
        get("/{uuid}") {
            // Get single document
        }
    }

    // Write operations require "documenten:write"
    withScopes("documenten:write") {
        post {
            // Create document
        }
        
        patch("/{uuid}") {
            // Update document
        }
    }

    // Delete requires both "documenten:write" and "documenten:admin"
    withScopes("documenten:write", "documenten:admin") {
        delete("/{uuid}") {
            // Delete document
        }
    }
}
```

### Method 4: Nested Scope Requirements

You can nest scopes for hierarchical protection:

```kotlin
// All routes require base "documenten" scope
withScopes("documenten:read") {
    route("/public") {
        get {
            // Requires only "documenten:read"
        }
    }

    // Admin routes require additional scope
    withScopes("documenten:admin") {
        route("/admin") {
            get {
                // Requires BOTH "documenten:read" AND "documenten:admin"
            }
        }
    }
}
```

## JWT Token Format

### Scope Claim Format

The JWT token should contain scopes in one of these formats:

**Option 1: Space-separated string (OAuth2 standard)**
```json
{
  "sub": "user123",
  "scope": "documenten:read documenten:write audittrail:read"
}
```

**Option 2: Array format**
```json
{
  "sub": "user123",
  "scope": ["documenten:read", "documenten:write", "audittrail:read"]
}
```

**Option 3: Scopes claim (alternative)**
```json
{
  "sub": "user123",
  "scopes": ["documenten:read", "documenten:write", "audittrail:read"]
}
```

## Scope Naming Convention

We recommend using a hierarchical naming convention:

- `<resource>:<action>` - e.g., `documenten:read`, `documenten:write`
- `<resource>:*` - wildcard for all actions on a resource
- `<resource>:<action>:<sub-resource>` - for nested resources

Examples:
- `documenten:read` - Read documents
- `documenten:write` - Create/update documents
- `documenten:delete` - Delete documents
- `documenten:*` - All document operations
- `audittrail:read` - Read audit trails
- `admin:*` - All admin operations

## Error Handling

When a user lacks required scopes, they receive a `403 Forbidden` response:

```json
{
  "error": "Insufficient permissions",
  "detail": "Required scopes: documenten:write, documenten:admin",
  "code": "insufficient_scope"
}
```

## Testing

### Test Tokens

For testing, generate JWT tokens with appropriate scopes:

```kotlin
// Generate test token with scopes
val token = JWT.create()
    .withIssuer("http://localhost:8081/realms/cg-dmf")
    .withSubject("testuser")
    .withClaim("scope", "documenten:read documenten:write")
    .sign(Algorithm.HMAC256("secret"))
```

### Testing Without Authentication

For development/testing, you can disable authentication:

```kotlin
application {
    documentenApiModule(useAuthentication = false)
}
```

## Keycloak Configuration

If using Keycloak, configure client scopes:

1. Create client scopes in Keycloak:
   - `documenten:read`
   - `documenten:write`
   - `documenten:admin`
   - `audittrail:read`

2. Assign scopes to client roles

3. Map roles to users/groups

4. Ensure the `scope` claim is included in JWT tokens

## Logging

The plugin logs scope checks at DEBUG level:

```
DEBUG: User scopes: [documenten:read, documenten:write]
DEBUG: Route requires scopes: [documenten:write]
DEBUG: Scope check passed
```

Enable debug logging to troubleshoot authorization issues:

```xml
<logger name="com.baseflow.api.middleware.ScopeAuthorizationPlugin" level="DEBUG"/>
```

## Common Patterns

### Public Routes

Routes without `withScopes()` are accessible to any authenticated user:

```kotlin
get("/health") {
    call.respond("OK") // No scope required, just authentication
}
```

### Service Accounts

For service-to-service communication, use service account tokens with appropriate scopes:

```
scope: "service:sync documenten:read documenten:write"
```

### Conditional Scopes

Check scopes programmatically within handlers:

```kotlin
get("/{uuid}") {
    val principal = call.principal<JWTPrincipal>()
    val scopes = principal?.payload?.getClaim("scope")?.asString()?.split(" ") ?: emptyList()
    
    if ("documenten:admin" in scopes) {
        // Include sensitive data
    } else {
        // Return limited data
    }
}
```

## Migration Guide

To add scope protection to existing routes:

1. **Identify routes**: List all routes that need protection
2. **Define scopes**: Choose appropriate scope names
3. **Wrap routes**: Use `withScopes()` to protect routes
4. **Update tokens**: Ensure JWT tokens include required scopes
5. **Test**: Verify access control works correctly

Example migration:

```kotlin
// Before
fun Route.documentRoutes() {
    get { /* handler */ }
    post { /* handler */ }
}

// After
fun Route.documentRoutes() {
    withScopes("documenten:read") {
        get { /* handler */ }
    }
    
    withScopes("documenten:write") {
        post { /* handler */ }
    }
}
```

## Best Practices

1. **Principle of least privilege**: Grant only necessary scopes
2. **Group by access level**: Use `withScopes()` to group routes with same requirements
3. **Consistent naming**: Follow the `resource:action` convention
4. **Document scopes**: Keep a list of all available scopes
5. **Test thoroughly**: Test both positive and negative cases
6. **Monitor access**: Log denied access attempts for security auditing
