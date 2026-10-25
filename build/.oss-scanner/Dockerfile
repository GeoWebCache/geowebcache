# Reproducible build and test environment for GeoWebCache.
# Build context: the repository root.
#   docker build -f build/.oss-scanner/Dockerfile -t geowebcache .
# Builds every module the way .github/workflows/linux.yml does, runs the unit tests, and leaves the Maven
# repository populated so the project can be built and tested again with no network (`mvn -o`).
# Aim to finish within about 40 minutes on a 16 CPU machine.
FROM eclipse-temurin:17-jdk-jammy@sha256:43f4431cc895d37ceb115e2e1f160545ec45caee9c1cb4246fc6bb879d58aeda

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978

ENV DEBIAN_FRONTEND=noninteractive \
    MAVEN_HOME=/opt/maven \
    PATH=/opt/maven/bin:$PATH \
    MAVEN_OPTS="-Xmx2g -Djava.awt.headless=true -Dmaven.wagon.http.retryHandler.count=3 -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dmaven.gitcommitid.skip=true -Daether.syncContext.named.factory=rwlock-local -Daether.syncContext.named.time=600"

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip procps \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL --retry 3 --retry-all-errors \
        "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz \
    && echo "${MAVEN_SHA512}  /tmp/maven.tar.gz" | sha512sum --check --strict - \
    && mkdir -p "${MAVEN_HOME}" \
    && tar -xzf /tmp/maven.tar.gz -C "${MAVEN_HOME}" --strip-components=1 \
    && rm /tmp/maven.tar.gz \
    && mvn --version

# zizmor, an analyser for GitHub Actions workflows. It is taken from its published wheel, because the wheel's Python
# package needs Python 3.11 and this image has none. The wheel is checked against the SHA-256 that PyPI publishes.
# Run it offline from /src with: zizmor --offline .
ARG ZIZMOR_VERSION=1.31.1
ARG ZIZMOR_URL_X86_64=https://files.pythonhosted.org/packages/7c/f7/ee2c8eedc7bf07f443a41aa12f828c7846fd17bab184c22403afd0cbb137/zizmor-1.31.1-py3-none-manylinux_2_28_x86_64.whl
ARG ZIZMOR_SHA256_X86_64=58e878eb2dbd2c6183c105e1ac28c14f7dd1eca44a783f1fe2a69647d012c410
ARG ZIZMOR_URL_AARCH64=https://files.pythonhosted.org/packages/17/a9/a2e0721c169d116074f07cddf98a939886d422acd393537efd99a331475b/zizmor-1.31.1-py3-none-manylinux_2_24_aarch64.whl
ARG ZIZMOR_SHA256_AARCH64=d9b36840c560a8abe39d9ef3d077239aabf6d572c5c2b629c970592a617dbf6f
RUN case "$(uname -m)" in \
      x86_64) url="$ZIZMOR_URL_X86_64"; sha="$ZIZMOR_SHA256_X86_64" ;; \
      aarch64) url="$ZIZMOR_URL_AARCH64"; sha="$ZIZMOR_SHA256_AARCH64" ;; \
      *) echo "WARNING: zizmor is not installed (no build for $(uname -m))"; exit 0 ;; \
    esac \
    && curl -fsSL --retry 3 --retry-all-errors -o /tmp/zizmor.whl "$url" \
    && echo "${sha}  /tmp/zizmor.whl" | sha256sum --check --strict - \
    && unzip -p /tmp/zizmor.whl "zizmor-${ZIZMOR_VERSION}.data/scripts/zizmor" > /usr/local/bin/zizmor \
    && chmod 755 /usr/local/bin/zizmor \
    && rm /tmp/zizmor.whl \
    && zizmor --version

# The checkout lives inside the image, at /src.
COPY . /src
WORKDIR /src

# Every module (-Dall, as CI does), test classes compiled. GeoTools SNAPSHOTs come from repo.osgeo.org.
RUN mvn -B -ntp -U -T1C -DskipTests -Dall -f geowebcache/pom.xml install \
    && (mvn -B -ntp -f geowebcache/core/pom.xml dependency:sources -DincludeGroupIds=org.geotools \
        || echo "WARNING: could not fetch GeoTools sources jars")

# The full unit test suite. This also downloads the surefire providers and every module's test-scoped
# dependencies, so `mvn -o ... surefire:test` works offline in all modules. Cloud blob store tests (S3, Azure,
# GCS, Swift) skip themselves without credentials. Failures do not stop the build. The timeout is a safety net
# that keeps a slow machine inside the overall build limit; on the machines measured the suite takes ~20 minutes.
RUN (timeout 1500 mvn -B -ntp -T1C -Dall -f geowebcache/pom.xml surefire:test --fail-never \
    || echo "WARNING: GeoWebCache tests reported failures or hit the timeout")

# Classpath for the embedded Jetty launcher in web/src/test (org.geowebcache.jetty.Start).
RUN mvn -B -ntp -f geowebcache/web/pom.xml dependency:build-classpath \
        -Dmdep.includeScope=test -Dmdep.outputFile=/opt/gwc-web.classpath

# Start the GWC webapp from web/src/main/webapp on :8081 in the background and wait until it answers.
# Default user (WEB-INF/users.properties): geowebcache / secured.
RUN printf '%s\n' \
      '#!/bin/bash' \
      'set -u' \
      'url="http://localhost:8081/geowebcache/service/wmts?REQUEST=GetCapabilities"' \
      'if curl -sf -o /dev/null "$url"; then exit 0; fi' \
      'export GEOWEBCACHE_CACHE_DIR=${GEOWEBCACHE_CACHE_DIR:-/tmp/gwc-cache}' \
      'mkdir -p "$GEOWEBCACHE_CACHE_DIR"' \
      'cd /src/geowebcache/web' \
      'nohup java ${JAVA_OPTS:--Xmx1g} -cp "target/classes:target/test-classes:$(cat /opt/gwc-web.classpath)" \' \
      '  org.geowebcache.jetty.Start >/var/log/geowebcache.log 2>&1 &' \
      'for _ in $(seq 180); do' \
      '  curl -sf -o /dev/null "$url" && { echo "GeoWebCache up at http://localhost:8081/geowebcache"; exit 0; }' \
      '  sleep 1' \
      'done' \
      'echo "geowebcache-start: server did not start, see /var/log/geowebcache.log" >&2' \
      'exit 1' \
    > /usr/local/bin/geowebcache-start \
    && chmod 0755 /usr/local/bin/geowebcache-start
