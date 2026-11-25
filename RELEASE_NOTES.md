## 1.1.2

### Backend

- Maximale grootte van HTTP-request-headers verhoogd van 32 KiB naar 128 KiB en instelbaar gemaakt via
  `MAX_HEADER_SIZE` (en de request-regel via `MAX_INITIAL_LINE_LENGTH`). Grote SSO-tokens met veel rollen of
  groepen in de `Authorization`-header leidden tot geweigerde verzoeken vanuit de admin portal
  ("An error occurred in the Server Components render" op de instellingenpagina's).
- De ingestelde limieten worden bij het opstarten gelogd.

### Admin Portal

- Node.js header-limiet (`--max-http-header-size`) gelijkgetrokken met de backend: 128 KiB.

### Helm chart

- Ingress `proxy-buffer-size` verhoogd naar 64k (8 buffers) voor grote cookie-/tokenheaders, voor zowel de
  backend- als de admin-portal-ingress.

## 1.1.1

### Backend

- Fixes null reference exception thrown by FlywayDB.
