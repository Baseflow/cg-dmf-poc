# Security Policy / Beveiligingsbeleid

## Reporting a vulnerability (English)

Report security issues by email to **security @ baseflow.com**. Do **not** open a public GitHub issue.

A useful report includes:
- A clear description of the vulnerability and what an attacker can achieve
- Step-by-step reproduction instructions or a minimal proof-of-concept
- Affected version(s) or commit(s)

We do **not** process automated scanner output, AI-generated reports, or any report that has not been manually verified for reproducibility and impact.

We will acknowledge receipt, assess the report, and coordinate a fix before public disclosure.

---

## Beveiligingsbeleid

## Kwetsbaarheden melden

Meld beveiligingsproblemen via e-mail naar **security @ baseflow.com**.

Maak **geen** publiek GitHub-issue aan voor beveiligingsproblemen.
Publieke disclosure vóór een beschikbare fix stelt gebruikers onnodig bloot aan risico.

## Wat we verwachten in een melding

Een bruikbaar beveiligingsrapport bevat:

- **Beschrijving** — een heldere omschrijving van de kwetsbaarheid en het aanvalsscenario. Geef aan wat een aanvaller kan bereiken bij succesvolle exploitatie.
- **Reproductiestappen** — concrete stappen of een minimaal codevoorbeeld om het probleem zelf te reproduceren. Rapporten die niet reproduceerbaar zijn, kunnen niet worden beoordeeld.
- **Betrokken versie(s)** — welke release(s) of commit(s) zijn getroffen.
- **Omgeving** — relevante configuratie (bijv. welke authenticatiemethode, opslagtype, netwerkopstelling).
- **Mogelijke oplossing** (optioneel) — als u een idee heeft hoe het probleem verholpen kan worden.

We verzoeken nadrukkelijk **geen** geautomatiseerde scanrapporten zonder inhoudelijke beoordeling te sturen. Rapporten die uitsluitend bestaan uit scanner-output, CVE-lijsten of AI-gegenereerde tekst zonder eigenhandige verificatie van reproduceerbaarheid en impact worden niet in behandeling genomen.

## Afhandeling

Na ontvangst van een melding:

1. We bevestigen de ontvangst zo spoedig mogelijk.
2. We beoordelen de melding en nemen contact op over ernst, impact en vervolgstappen.
3. We werken aan een oplossing en stemmen de openbaarmakingstermijn af met de melder.
4. Na het uitbrengen van een fix publiceren we een beveiligingsadvies in de release notes.

We hanteren een beleid van **gecoördineerde openbaarmaking**: we vragen melders de kwetsbaarheid niet openbaar te maken totdat er een oplossing beschikbaar is, of totdat we gezamenlijk een andere afspraak hebben gemaakt.

## Scope

Dit beleid geldt voor de CG-DMF codebase in deze repository: backend (Kotlin/Ktor), admin portal (Next.js) en Helm chart.

Afhankelijkheden van derden (Ktor, Exposed, Keycloak, PostgreSQL) vallen buiten scope — meld kwetsbaarheden daarin bij de respectievelijke projecten.
