# Changelog

## [0.3.0] — 2026-06-01

### Breaking changes

**`settings.s3` renamed to `settings.blobStorage`**

The `settings.s3` values block has been replaced by `settings.blobStorage` to reflect that the
application now supports multiple storage backends (S3-compatible and Azure Blob Storage) and
configures them via the `BLOB_STORAGE_*` environment variables rather than the removed `S3_*` /
`MINIO_*` variables.

**Migration — update your values file:**

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

**Secret rename**

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

---

## [0.2.0]

Initial public release.