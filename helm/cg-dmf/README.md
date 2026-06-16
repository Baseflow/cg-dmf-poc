# cg-dmf Helm Chart

Helm chart for the **Document Management Facility (DMF)** — a Proof-of-Concept implementation of the
Common Ground [Documenten API (DRC)](https://vng-realisatie.github.io/gemma-zaken/standaard/documenten/)
written in Kotlin.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.x
- A PostgreSQL database
- A S3-compatible object store or Azure Blob Storage
- An OpenZaak instance (or set `settings.openzaak.validationEnabled: false` to skip validation)
- An OIDC-compatible identity provider (e.g. Keycloak)

## Installation

### 1. Add required values

Create a `my-values.yaml` with at minimum the sensitive credentials and the URLs for your environment:

```yaml
settings:
    baseUrl: "https://cg-dmf.example.com"
    oidcIssuer: "https://auth.example.com/realms/my-realm"

    database:
        url: "jdbc:postgresql://my-postgres:5432/documenten"
        username: "documenten"
        password: "changeme"

    blobStorage:
        name: "production"
        type: "S3"
        url: "https://s3.example.com"
        bucket: "cg-dmf"
        accessKey: "myaccesskey"
        secretKey: "mysecretkey"

    openzaak:
        endpoint: "https://openzaak.example.com"
        clientId: "cg-dmf"
        clientSecret: "changeme"
```

### 2. Install the chart

```shell
helm install cg-dmf ./helm/cg-dmf -f my-values.yaml --namespace cg-dmf --create-namespace
```

### 3. Upgrade

```shell
helm upgrade cg-dmf ./helm/cg-dmf -f my-values.yaml --namespace cg-dmf
```

### 4. Uninstall

```shell
helm uninstall cg-dmf --namespace cg-dmf
```

> **Note:** Secrets are annotated with `helm.sh/resource-policy: keep` and will **not** be deleted on
> uninstall. Remove them manually if needed:
>
> ```shell
> kubectl delete secret cg-dmf-database cg-dmf-blob-storage cg-dmf-openzaak -n cg-dmf
> ```

## Exposing the API via Ingress

Enable and configure the Ingress in your values file:

```yaml
ingress:
    enabled: true
    ingressClassName: nginx
    annotations:
        nginx.ingress.kubernetes.io/proxy-body-size: "0"
        nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
        nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
        cert-manager.io/cluster-issuer: letsencrypt-production
    hosts:
        - host: cg-dmf.example.com
          paths:
              - path: /
                pathType: ImplementationSpecific
    tls:
        - secretName: cg-dmf-tls-secret
          hosts:
              - cg-dmf.example.com
```

## Supplying credentials from an external secret manager

The chart by default creates its own Kubernetes Secrets from the values you provide.

If you manage secrets externally (e.g. Azure Key Vault via the AKV-to-K8s operator,
Sealed Secrets, or External Secrets Operator), create the secrets yourself with the
correct keys and then set `existingSecret` to the **name of your pre-existing Secret**.
The chart will skip creating its own Secret and reference the name you provided instead:

```yaml
settings:
    database:
        existingSecret: "my-db-secret" # must contain: DB_URL, DB_USER, DB_PASSWORD
    blobStorage:
        existingSecret: "my-blob-storage-secret" # must contain: BLOB_STORAGE_ACCESS_KEY1, BLOB_STORAGE_SECRET_KEY1
    openzaak:
        existingSecret: "my-openzaak-secret" # must contain: OPENZAAK_CLIENT_SECRET
```

Leave `existingSecret` empty or `null` (the default) to have the chart create and manage the
Secret automatically. The chart-managed names follow the pattern `<fullname>-database`,
`<fullname>-blob-storage`, and `<fullname>-openzaak`.

Alternatively, set the actual secret values through your CD pipeline using `--set`:

```shell
helm upgrade cg-dmf ./helm/cg-dmf \
  --reuse-values \
  --set settings.database.password="$DB_PASSWORD" \
  --set settings.blobStorage.accessKey="$BLOB_STORAGE_ACCESS_KEY1" \
  --set settings.blobStorage.secretKey="$BLOB_STORAGE_SECRET_KEY1" \
  --set settings.openzaak.clientSecret="$OPENZAAK_CLIENT_SECRET"
```

## Scaling

Enable the Horizontal Pod Autoscaler:

```yaml
replicaCount: 2

autoscaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 10
    targetCPUUtilizationPercentage: 80
    targetMemoryUtilizationPercentage: 80
```

## Configuration reference

### General

| Key                | Type   | Default | Description                                                         |
| ------------------ | ------ | ------- | ------------------------------------------------------------------- |
| `replicaCount`     | int    | `1`     | Number of pod replicas. Ignored when `autoscaling.enabled` is true. |
| `nameOverride`     | string | `""`    | Override the chart name portion of resource names.                  |
| `fullnameOverride` | string | `""`    | Fully override the resource name prefix.                            |
| `imagePullSecrets` | list   | `[]`    | List of image pull secret names.                                    |

### Image

| Key                | Type   | Default                          | Description                                      |
| ------------------ | ------ | -------------------------------- | ------------------------------------------------ |
| `image.repository` | string | `baseflow.azurecr.io/cg-dmf-poc` | Container image repository.                      |
| `image.tag`        | string | `""`                             | Image tag. Defaults to the chart's `appVersion`. |
| `image.pullPolicy` | string | `IfNotPresent`                   | Image pull policy.                               |

### Service account

| Key                          | Type   | Default | Description                                           |
| ---------------------------- | ------ | ------- | ----------------------------------------------------- |
| `serviceAccount.create`      | bool   | `true`  | Whether to create a dedicated ServiceAccount.         |
| `serviceAccount.name`        | string | `""`    | Name to use. Auto-generated from fullname when empty. |
| `serviceAccount.annotations` | object | `{}`    | Annotations to add to the ServiceAccount.             |

### Pod

| Key                  | Type   | Default           | Description                                                                          |
| -------------------- | ------ | ----------------- | ------------------------------------------------------------------------------------ |
| `podAnnotations`     | object | `{}`              | Annotations added to every pod.                                                      |
| `podLabels`          | object | `{}`              | Extra labels added to every pod.                                                     |
| `podSecurityContext` | object | `{fsGroup: 1000}` | Pod-level security context.                                                          |
| `securityContext`    | object | see values.yaml   | Container-level security context. Runs as non-root UID 1000, drops all capabilities. |
| `nodeSelector`       | object | `{}`              | Node selector for pod placement.                                                     |
| `tolerations`        | list   | `[]`              | Tolerations for pod placement.                                                       |
| `affinity`           | object | `{}`              | Affinity rules for pod placement.                                                    |

### Service

| Key            | Type   | Default     | Description                                                |
| -------------- | ------ | ----------- | ---------------------------------------------------------- |
| `service.type` | string | `ClusterIP` | Kubernetes Service type.                                   |
| `service.port` | int    | `80`        | Port exposed by the Service (maps to container port 8080). |

### Ingress

| Key                        | Type   | Default         | Description                             |
| -------------------------- | ------ | --------------- | --------------------------------------- |
| `ingress.enabled`          | bool   | `false`         | Enable Ingress resource creation.       |
| `ingress.ingressClassName` | string | `nginx`         | Ingress class name.                     |
| `ingress.annotations`      | object | `{}`            | Annotations to add to the Ingress.      |
| `ingress.hosts`            | list   | see values.yaml | List of host/path rules.                |
| `ingress.tls`              | list   | `[]`            | TLS configuration (secretName + hosts). |

### Resources

| Key         | Type   | Default | Description                                                                                 |
| ----------- | ------ | ------- | ------------------------------------------------------------------------------------------- |
| `resources` | object | `{}`    | CPU/memory requests and limits. Not set by default — configure consciously per environment. |

### Probes

| Key              | Type   | Default                            | Description                                                                |
| ---------------- | ------ | ---------------------------------- | -------------------------------------------------------------------------- |
| `startupProbe`   | object | `GET /health/liveness, 30×10s`     | Startup probe. Allows up to 5 minutes for the JVM to start.                |
| `livenessProbe`  | object | `GET /health/liveness, every 30s`  | Liveness probe. Restarts the container if it fails 3 times.                |
| `readinessProbe` | object | `GET /health/readiness, every 20s` | Readiness probe. Removes the pod from the Service endpoints while failing. |

### Autoscaling

| Key                                             | Type | Default | Description                           |
| ----------------------------------------------- | ---- | ------- | ------------------------------------- |
| `autoscaling.enabled`                           | bool | `false` | Enable Horizontal Pod Autoscaler.     |
| `autoscaling.minReplicas`                       | int  | `1`     | Minimum number of replicas.           |
| `autoscaling.maxReplicas`                       | int  | `5`     | Maximum number of replicas.           |
| `autoscaling.targetCPUUtilizationPercentage`    | int  | `80`    | Target CPU utilization percentage.    |
| `autoscaling.targetMemoryUtilizationPercentage` | int  | `80`    | Target memory utilization percentage. |

### Escape hatches

| Key                 | Type | Default | Description                                                                                                  |
| ------------------- | ---- | ------- | ------------------------------------------------------------------------------------------------------------ |
| `extraEnvVars`      | list | `[]`    | Extra environment variables injected into the container. Supports Helm templating via `tplvalues.render`.    |
| `extraVolumes`      | list | `[]`    | Extra volumes added to the **pod spec** — defines the volume _source_ (ConfigMap, Secret, PVC, emptyDir, …). |
| `extraVolumeMounts` | list | `[]`    | Extra volume mounts added to the **container** — defines _where_ a volume is mounted inside the container.   |

`extraVolumes` and `extraVolumeMounts` always work as a pair: a volume without a mount does nothing, and a mount without
a matching volume name will prevent the pod from starting. The `name` field must match between the two.

**Example — mounting a custom CA certificate:**

```yaml
extraVolumes:
    - name: custom-ca
      configMap:
          name: my-ca-bundle # a ConfigMap you created separately

extraVolumeMounts:
    - name: custom-ca # must match the volume name above
      mountPath: /etc/ssl/certs/custom-ca.crt
      subPath: ca.crt
      readOnly: true
```

### Application settings

#### General (`settings`)

| Key                            | Type   | Default                                   | Description                                                                                                                                                                                                                                                                      |
| ------------------------------ | ------ | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `settings.baseUrl`             | string | `https://cg-dmf.example.com`              | Public base URL returned in API self-links. Must match the externally reachable URL.                                                                                                                                                                                             |
| `settings.oidcIssuer`          | string | `https://auth.example.com/realms/valtimo` | OIDC issuer URL used to validate incoming JWTs.                                                                                                                                                                                                                                  |

#### Database (`settings.database`)

| Key                                | Type   | Default                                      | Description                                                                                                                                                                                                                                               |
| ---------------------------------- | ------ | -------------------------------------------- |-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `settings.database.existingSecret` | string | `null`                                       | Name of a pre-existing Secret containing database credentials (keys: `DB_URL`, `DB_USER`, `DB_PASSWORD`). When set, the chart skips Secret creation and references this name. Defaults to the chart-managed Secret `<fullname>-database` when empty/null. |
| `settings.database.url`            | string | `jdbc:postgresql://postgres:5432/documenten` | JDBC connection URL for PostgreSQL.                                                                                                                                                                                                                       |
| `settings.database.username`       | string | `documenten`                                 | Database username.                                                                                                                                                                                                                                        |
| `settings.database.password`       | string | **required**                                 | Database password. Stored in the `<fullname>-database` Secret.                                                                                                                                                                                            |
| `settings.database.poolSize`       | int    | `null` (app default: 10)                     | Maximum HikariCP connection pool size (`DB_POOL_SIZE`). Leave null to use the application default.                                                                                                                                                        |

#### Blob storage (`settings.blobStorage`)

Configures the primary blob storage repository (mapped to `BLOB_STORAGE_*1` env vars).
Use `extraEnvVars` to configure additional repositories (`BLOB_STORAGE_*2`, etc.).

| Key                                          | Type   | Default             | Description                                                                                                                                                                                                                                              |
| -------------------------------------------- | ------ | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `settings.blobStorage.existingSecret`        | string | `null`              | Name of a pre-existing Secret containing credentials (keys: `BLOB_STORAGE_ACCESS_KEY1`, `BLOB_STORAGE_SECRET_KEY1`). When set, the chart skips Secret creation and references this name. Defaults to the chart-managed Secret `<fullname>-blob-storage`. |
| `settings.blobStorage.name`                  | string | `default`           | Human-readable repository name shown in logs (`BLOB_STORAGE_NAME1`).                                                                                                                                                                                    |
| `settings.blobStorage.type`                  | string | `S3`                | Storage type: `S3` or `Azure Blob Storage` (`BLOB_STORAGE_TYPE1`).                                                                                                                                                                                      |
| `settings.blobStorage.url`                   | string | `http://minio:9000` | Endpoint URL (`BLOB_STORAGE_URL1`).                                                                                                                                                                                                                      |
| `settings.blobStorage.bucket`                | string | `cg-dmf`            | Bucket (S3) or container (Azure) name (`BLOB_STORAGE_BUCKET1`).                                                                                                                                                                                          |
| `settings.blobStorage.accessKey`             | string | **required**        | Access key (S3) or account name (Azure). Stored in the `<fullname>-blob-storage` Secret.                                                                                                                                                                 |
| `settings.blobStorage.secretKey`             | string | **required**        | Secret key (S3) or account key (Azure). Stored in the `<fullname>-blob-storage` Secret.                                                                                                                                                                  |
| `settings.blobStorage.region`                | string | `""`                | Region (S3 only, optional — `BLOB_STORAGE_REGION1`). Omitted from env when empty.                                                                                                                                                                       |
| `settings.blobStorage.disableChecksums`      | bool   | `false`             | Disable checksum negotiation (S3 only — `BLOB_STORAGE_DISABLE_CHECKSUMS1`). Set to `true` when the endpoint does not support AWS checksum extensions.                                                                                                    |
| `settings.blobStorage.disableChunkedEncoding`| bool   | `false`             | Disable chunked encoding (S3 only — `BLOB_STORAGE_DISABLE_CHUNKED_ENCODING1`). Set to `true` when the endpoint or a proxy does not support chunked transfer encoding.                                                                                    |

#### OpenZaak integration (`settings.openzaak`)

| Key                                   | Type   | Default                        | Description                                                                                                                                                                                                                                    |
| ------------------------------------- | ------ | ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `settings.openzaak.existingSecret`    | string | `null`                         | Name of a pre-existing Secret containing OpenZaak credentials (key: `OPENZAAK_CLIENT_SECRET`). When set, the chart skips Secret creation and references this name. Defaults to the chart-managed Secret `<fullname>-openzaak` when empty/null. |
| `settings.openzaak.endpoint`          | string | `https://openzaak.example.com` | Base URL of the OpenZaak instance.                                                                                                                                                                                                             |
| `settings.openzaak.clientId`          | string | `cg-dmf`                       | OAuth2 client ID used when calling OpenZaak.                                                                                                                                                                                                   |
| `settings.openzaak.clientSecret`      | string | **required**                   | OAuth2 client secret. Stored in the `<fullname>-openzaak` Secret.                                                                                                                                                                              |
| `settings.openzaak.validationEnabled` | bool   | `false`                        | Whether to validate object references against OpenZaak. Set to `true` in production.                                                                                                                                                           |

## Secrets

Unless `existingSecret` is set, the chart creates three Kubernetes Secrets. The names follow the
pattern `<fullname>-<component>` (where `<fullname>` is the computed release full name):

| Secret name                  | Keys                                                     |
| ---------------------------- | -------------------------------------------------------- |
| `<fullname>-database`        | `DB_URL`, `DB_USER`, `DB_PASSWORD`                       |
| `<fullname>-blob-storage`    | `BLOB_STORAGE_ACCESS_KEY1`, `BLOB_STORAGE_SECRET_KEY1`   |
| `<fullname>-openzaak`        | `OPENZAAK_CLIENT_SECRET`                                 |

When using externally-managed secrets, set `existingSecret` to the name of your Secret — the chart
will skip creation and reference that name directly in the Deployment.

All three are annotated with `helm.sh/resource-policy: keep` so they survive a `helm uninstall`.

## Admin Portal

The chart can optionally deploy the **cg-dmf Admin Portal** (Next.js frontend) alongside the API.
Enable it with `adminPortal.enabled: true`.

### Enabling the Admin Portal

```yaml
adminPortal:
  enabled: true
  image:
    tag: "1.0.0"
  ingress:
    enabled: true
    ingressClassName: nginx
    annotations:
      cert-manager.io/cluster-issuer: letsencrypt-production
    hosts:
      - host: admin.cg-dmf.example.com
        paths:
          - path: /
            pathType: ImplementationSpecific
    tls:
      - secretName: cg-dmf-admin-tls-secret
        hosts:
          - admin.cg-dmf.example.com
  settings:
    nextauthUrl: "https://admin.cg-dmf.example.com"
    nextauthSecret: "your-nextauth-secret"
    keycloakUrl: "https://auth.example.com"
    keycloakRealm: "valtimo"
    keycloakClientId: "dmf-dashboard"
    keycloakClientSecret: "your-client-secret"
    backendUrl: "https://cg-dmf.example.com"
```

### Admin Portal Secret

The admin portal creates one Secret (unless `adminPortal.settings.existingSecret` is set):

| Secret name                   | Keys                                        |
| ----------------------------- | ------------------------------------------- |
| `<fullname>-admin-portal`     | `NEXTAUTH_SECRET`, `KEYCLOAK_CLIENT_SECRET` |

### Configuration reference: Admin Portal (`adminPortal`)

| Key | Type | Default | Description |
| --- | ---- | ------- | ----------- |
| `adminPortal.enabled` | bool | `false` | Enable the admin portal deployment. |
| `adminPortal.replicaCount` | int | `1` | Number of replicas. |
| `adminPortal.image.repository` | string | `baseflow.azurecr.io/cg-dmf-admin-portal` | Image repository. |
| `adminPortal.image.tag` | string | `""` | Image tag. Defaults to the chart's `appVersion`. |
| `adminPortal.image.pullPolicy` | string | `IfNotPresent` | Image pull policy. |
| `adminPortal.podAnnotations` | object | `{}` | Annotations on admin portal pods. |
| `adminPortal.service.type` | string | `ClusterIP` | Service type. |
| `adminPortal.service.port` | int | `80` | Service port. |
| `adminPortal.service.nodePort` | int | `null` | NodePort (when type is `NodePort`). |
| `adminPortal.ingress.enabled` | bool | `false` | Enable Ingress for the admin portal. |
| `adminPortal.ingress.ingressClassName` | string | `nginx` | Ingress class name. |
| `adminPortal.ingress.annotations` | object | `{}` | Ingress annotations. |
| `adminPortal.ingress.hosts` | list | see values.yaml | Host/path rules. |
| `adminPortal.ingress.tls` | list | `[]` | TLS configuration (secretName + hosts). |
| `adminPortal.resources` | object | `{}` | CPU/memory requests and limits. |
| `adminPortal.extraEnvVars` | list | `[]` | Extra environment variables injected into the container. Supports Helm templating via `tplvalues.render`. |
| `adminPortal.extraVolumes` | list | `[]` | Extra volumes added to the pod spec. |
| `adminPortal.extraVolumeMounts` | list | `[]` | Extra volume mounts added to the container. |
| `adminPortal.settings.existingSecret` | string | `null` | Pre-existing Secret name. When set, the chart skips Secret creation and references this name. |
| `adminPortal.settings.nextauthUrl` | string | `https://admin.cg-dmf.example.com` | Public URL of the admin portal, used by NextAuth for callback URLs. |
| `adminPortal.settings.nextauthSecret` | string | **required** | Secret key for encrypting NextAuth session tokens. |
| `adminPortal.settings.keycloakUrl` | string | `https://auth.example.com` | Base Keycloak URL without realm path. |
| `adminPortal.settings.keycloakRealm` | string | `valtimo` | Keycloak realm name. |
| `adminPortal.settings.keycloakClientId` | string | `dmf-dashboard` | Keycloak OAuth client ID. |
| `adminPortal.settings.keycloakClientSecret` | string | `""` | Keycloak OAuth client secret. Leave empty when using public clients (no secret required). |
| `adminPortal.settings.backendUrl` | string | `https://cg-dmf.example.com` | Backend API URL the admin portal proxies requests to. |
