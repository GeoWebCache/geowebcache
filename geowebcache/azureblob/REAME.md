# Azure BlobStore

GeoWebCache `BlobStore` implementation to store tiles on [Azure Blob Storage](https://azure.microsoft.com/en-us/products/storage/blobs)


## Building

`mvn install|test|verify` will run only unit tests.

In order to run the integration tests, build with the `-Ponline` maven profile. For example:

```
mvn verify -Ponline
```

There are two sets of integration tests:

- `org.geowebcache.azure.tests.container.*IT.java`: run integration tests using [Testcontainers](https://testcontainers.com/), with a Microsoft [Azurite](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite) Docker image.
- `org.geowebcache.azure.tests.online.*IT.java`: run integration tests against a real Azure account, only if there's a configuration file in `$HOME/.gwc_azure_tests.properties`, which must have the following contents:

```
accountName=<Azure account name>
accountKey=<Azure account key>
container=<Azure blob storage container name>
useHTTPS=true
maxConnections=<optional, max number of concurrent connections>
```

## Continuous integration

There's a Github Actions CI job defined at `<gwc git root>/.github/workflows/azure-integration.yml`
that will run the Docker-based integration tests first, and then the online ones against
a real Azure account, using Github repository secrets to define the values for the
`$HOME/.gwc_azure_tests.properties` file's `accountName`, `accountSecret`, and `container` keys.
