# syntax=docker/dockerfile:1

# Multi-stage build for the project-management backend (server deployment /
# fresh-database test). BUILD CONTEXT = the parent `projects/` directory
# (ai2/projects) — that is the layout the private compose files reference:
#   docker build -f homelabs-projects/Dockerfile -t homelabs-projects <projects-dir>
#
# The build compiles this repo's modules directly; the only module the image
# needs is `app` (the REST API server). The MCP adapter module is composed by
# the gateway image and deliberately plays no part here, so there is no sibling
# checkout to build first.
#
# The runtime stage ships ONLY the packaged fat jar. There are no native
# libraries in this service (no ONNX/DJL), so no `--enable-native-access` flag
# and no model mount are involved — same as `java -jar app/target/…jar` on the
# host. Tests (Testcontainers) need a Docker daemon: they run on the host via
# `mvn verify`, not inside the build.

# ---- Stage 1: build the jar (JDK 25, same Maven line as the host) ----
# Needs BuildKit/buildx (Docker's default builder since 23.x): the .m2 cache
# mount survives pom.xml changes, so dependency re-downloads stay rare.
FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /build

# Parent pom + app pom first, so the dependency layer is cached across source
# changes. The aggregator resolves as `../pom.xml` from the app module.
COPY homelabs-projects/pom.xml ./pom.xml
COPY homelabs-projects/app/pom.xml ./app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    cd app && mvn -B -ntp dependency:go-offline

COPY homelabs-projects/app/src ./app/src
RUN --mount=type=cache,target=/root/.m2 \
    cd app && mvn -B -ntp package -DskipTests

# ---- Stage 2: runtime (JRE 25, glibc-based) ----
FROM eclipse-temurin:25-jre-noble

# Non-root service user: the noble base image already ships an `ubuntu` user
# (uid/gid 1000) — reuse it instead of creating a duplicate-uid user.
# uid 1000 matches the usual first login user on the host, so the attachments
# volume lines up.
WORKDIR /opt/projects

# Container-canonical attachment location: the bytes live on a deployment
# volume mounted at runtime (loose coupling), while the database stores only
# RELATIVE storage keys. Override the env var to move the mount point.
ENV PROJECTS_ATTACHMENT_ROOT=/srv/attachments

RUN mkdir -p /srv/attachments \
 && chown -R ubuntu:ubuntu /srv/attachments /opt/projects

COPY --from=build --chown=ubuntu:ubuntu \
     /build/app/target/homelabs-projects-0.0.1-SNAPSHOT.jar app.jar

USER ubuntu
EXPOSE 8090

# JAVA_TOOL_OPTIONS is picked up by the JVM automatically (heap sizing etc.).
ENTRYPOINT ["java", "-jar", "/opt/projects/app.jar"]
