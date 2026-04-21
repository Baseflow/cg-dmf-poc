# OIDC Secret Reveal — Ontwerp

**Datum:** 2026-04-21

## Samenvatting

Toon het gedecrypte OIDC client secret in de leesweergave van de beheerportal, met een oog-toggle om de waarde te maskeren of zichtbaar te maken. Het secret wordt meegestuurd in de bestaande GET-response.

## Aanpak

Aanpak A: het plaintext secret wordt altijd meegestuurd in de GET `/admin/oidc-settings` response. Dit is acceptabel omdat:
- Het endpoint achter HTTPS en Bearer-token authenticatie zit.
- Dit het standaardpatroon is voor beheerportals (cloud-consoles, wachtwoordmanagers).
- Aandachtspunt: zorg dat de backend geen response bodies logt.

## Backend

### `OidcSettingsModels.kt`

Voeg `clientSecret: String?` toe aan `OidcSettingsResponse`:

```kotlin
@Serializable
data class OidcSettingsResponse(
    val issuer: String,
    val clientId: String,
    val hasSecret: Boolean,
    val clientSecret: String?,
    val updatedAt: String,
)
```

### `OidcSettingsRoutes.kt`

Update `toResponse()` om het secret te decrypten:

```kotlin
private fun OidcSettingsEntity.toResponse() = OidcSettingsResponse(
    issuer = issuer,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    clientSecret = clientSecretEncrypted?.let { OidcCrypto.decrypt(it) },
    updatedAt = updatedAt.toString(),
)
```

## Frontend

### `page.tsx`

**Interface-uitbreiding:**

```typescript
interface OidcSettings {
  issuer: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}
```

**State:**

Voeg `currentSecret` state toe (`string | null`, initieel `null`). Wordt gevuld na fetch en na opslaan vanuit de GET-response. Bestaande `showSecret` state wordt hergebruikt voor de toggle in de leesweergave.

**Leesweergave "Client secret" veld:**

- Als `currentSecret` aanwezig is:
  - Standaard: `••••••••••••`
  - Na klik oog-icoon: plaintext waarde
  - Oog-icoon rechts naast de tekst
- Als geen secret geconfigureerd: _"Niet geconfigureerd"_ (ongewijzigd)

**Reset-gedrag:**

`showSecret` reset naar `false` bij cancel en na succesvol opslaan — consistent met het bestaande gedrag in het bewerkingsformulier.

## Dataflow

```
GET /admin/oidc-settings
  → backend decrypteert clientSecretEncrypted
  → response: { issuer, clientId, hasSecret, clientSecret, updatedAt }
  → frontend slaat clientSecret op in currentSecret state
  → leesweergave: ••••••••  [oog-icoon]
  → klik oog → toont plaintext uit currentSecret
```

## Wijzigingsomvang

| Bestand | Wijziging |
|---|---|
| `src/main/kotlin/api/models/OidcSettingsModels.kt` | Voeg `clientSecret: String?` toe aan response model |
| `src/main/kotlin/api/admin/routes/OidcSettingsRoutes.kt` | Decrypteer secret in `toResponse()` |
| `frontend/admin-portal/app/instellingen/oidc/page.tsx` | Interface, state, en leesweergave-toggle |
