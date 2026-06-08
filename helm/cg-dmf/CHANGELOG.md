# Changelog

## [0.3.0] — unreleased

### Features

**Admin Portal**

Added optional admin portal deployment (`adminPortal.enabled`), with its own Deployment, Service,
ConfigMap, Secret, and Ingress resources. See the README for configuration reference and the
`adminPortal.*` values for all options.

### Breaking changes

**`settings.encryption` — new required fields**

`settings.encryption.secretKey` and `settings.encryption.salt` are now required to enable
AES-256-PBE-GCM encryption of stored blob-storage credentials. Existing deployments must add these
values before upgrading or the chart will fail at render time.

Generate the values:

```bash
openssl rand -base64 32   # secretKey — free-form passphrase
openssl rand -hex 8       # salt — must be a valid hex string
```

> **Important:** use the same values across all deployments that share a database. Changing them
> makes existing encrypted credentials unreadable.

To hand off to an external secret manager instead, set `settings.encryption.existingSecret` to the
name of a pre-existing Secret containing `ENCRYPTION_SECRET_KEY` and `ENCRYPTION_SALT`.

**Selector label change**

The main backend Deployment now includes `app.kubernetes.io/component: backend` in its selector
labels. Because `spec.selector.matchLabels` is immutable on existing Deployments, upgrading from
0.2.x requires deleting the old Deployment first:

```bash
kubectl delete deployment cg-dmf -n <namespace>
helm upgrade cg-dmf ./helm/cg-dmf -f <values-file> --namespace <namespace>
```

This causes brief downtime for the backend while the new Deployment is created. The Service,
Secrets, and ConfigMap are unaffected.

**`securityContext.readOnlyRootFilesystem` now defaults to `true`**

The backend container already mounted an emptyDir at `/tmp`; the default value now reflects that.
Override to `false` if your workload writes outside `/tmp`.

**`settings.s3` renamed to `settings.blobStorage`**

The `settings.s3` values block has been replaced by `settings.blobStorage` to reflect that the
application now supports multiple storage backends (S3-compatible and Azure Blob Storage) and
configures them via the `BLOB_STORAGE_*` environment variables rather than the removed `S3_*` /
`MINIO_*` variables.

Migration — update your values file:

```yaml
# Before (0.2.x)
settings:
  s3:
    existingSecret: null        # or "my-s3-secret"
    endpoint: "http://minio:9000"
    bucket: "cg-dmf"
    accessKey: "mykey"
    secretKey: "mysecret"
    disableChecksums: false
    disableChunkedEncoding: false

# After (0.3.0)
settings:
  blobStorage:
    existingSecret: null        # or "my-blob-storage-secret"
    name: "default"             # new: human-readable name shown in logs
    type: "S3"                  # new: "S3" or "Azure Blob Storage"
    url: "http://minio:9000"    # was: endpoint
    bucket: "cg-dmf"
    accessKey: "mykey"
    secretKey: "mysecret"
    region: ""                  # new: optional, S3 only
    disableChecksums: false
    disableChunkedEncoding: false
```

The chart-managed Kubernetes Secret is renamed from `<fullname>-s3` to `<fullname>-blob-storage`,
and its keys change from `S3_ACCESS_KEY` / `S3_SECRET_KEY` to `BLOB_STORAGE_ACCESS_KEY1` /
`BLOB_STORAGE_SECRET_KEY1`.

If you use `existingSecret`, update the secret name you provide and ensure it contains the new key
names. If the chart manages the secret, delete the old one after upgrading (it is annotated with
`helm.sh/resource-policy: keep` and will not be removed automatically):

```shell
kubectl delete secret <fullname>-s3 -n <namespace>
```

**Additional repositories**
To configure more than one blob storage repository, use `extraEnvVars` with the next index:

```yaml
extraEnvVars:
  - name: BLOB_STORAGE_NAME2
    value: "archive"
  - name: BLOB_STORAGE_TYPE2
    value: "S3"
  - name: BLOB_STORAGE_URL2
    value: "https://s3.example.com"
  - name: BLOB_STORAGE_ACCESS_KEY2
    value: "key2"
  - name: BLOB_STORAGE_SECRET_KEY2
    value: "secret2"
  - name: BLOB_STORAGE_BUCKET2
    value: "archive-bucket"
```

### Improvements

**Admin Portal escape hatches**

`adminPortal.extraEnvVars`, `adminPortal.extraVolumes`, and `adminPortal.extraVolumeMounts` are now
supported, consistent with the backend.

**Simplified Ingress template**

The backend Ingress template now unconditionally emits `networking.k8s.io/v1`, aligning with the
documented minimum requirement of Kubernetes 1.19+. The dead code paths for
`networking.k8s.io/v1beta1` and `extensions/v1beta1` have been removed.

**`helm test` support**

A connection test pod is now included. Run `helm test <release>` after installing or upgrading to
verify the backend (and admin portal, when enabled) is reachable.

**Removed redundant probe `initialDelaySeconds`**

`initialDelaySeconds` on the liveness and readiness probes is a no-op when a startup probe is
configured. The fields have been removed to reduce noise.

---

## [0.2.0]

Initial public release.
