## 0.9.8

### Backend
* When disableChunkedEncoding is true, do not use data streams. This is for
  the S3-proxy, which doesn't handle signing and streaming.
