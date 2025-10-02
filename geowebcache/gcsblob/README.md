# GCS Blob Store

BlobStore implementation for Google Cloud Storage.

## Overview

This module provides a `BlobStore` that stores tiles in a GCS bucket. Tiles are organized using the standard TMS key structure: `<prefix>/<layer>/<gridset>/<format>/<parameters>/<z>/<x>/<y>.<ext>`

## Components

- `GoogleCloudStorageBlobStore` - Main BlobStore implementation
- `GoogleCloudStorageClient` - Low-level GCS operations, handles batch deletes via background thread pool
- `GoogleCloudStorageBlobStoreInfo` - XStream-serializable configuration
- `GoogleCloudStorageConfigProvider` - Spring integration for config management

## Building

```bash
mvn clean install
```

## Testing

Unit tests run against a fake GCS server via testcontainers:

```bash
mvn test
```

Integration tests use the same fake GCS server but run through the failsafe plugin:

```bash
mvn verify -Ponline
```

## Configuration

Supports environment variable expansion in all config parameters. Example:

```xml
<GoogleCloudStorageBlobStore>
  <id>gcs-store</id>
  <enabled>true</enabled>
  <bucket>${GCS_BUCKET}</bucket>
  <prefix>gwc</prefix>
  <projectId>${GCS_PROJECT_ID}</projectId>
  <useDefaultCredentialsChain>true</useDefaultCredentialsChain>
</GoogleCloudStorageBlobStore>
```

### Parameters

- `bucket` (required) - GCS bucket name
- `prefix` (optional) - Path prefix within the bucket. If not set, operates at bucket root
- `projectId` (optional) - GCP project ID
- `quotaProjectId` (optional) - Project to bill for quota (for requester-pays buckets)
- `endpointUrl` (optional) - Custom endpoint URL for emulators or GCS-compatible services
- `useDefaultCredentialsChain` (optional) - Set to `true` to use Application Default Credentials
- `apiKey` (optional) - API key for authentication

Authentication options (pick one):
- `useDefaultCredentialsChain` - Uses Application Default Credentials
- `apiKey` - API key for simple auth
- No auth specified - Anonymous access (useful for emulators)

## Notes

Delete operations run asynchronously on a background thread pool sized to available processors. The pool shuts down gracefully on blob store destruction with a 60s timeout.
