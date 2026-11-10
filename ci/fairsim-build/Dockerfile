# fairSIM build image: JDK (8, 21 or 25), Maven 3.9, Node 24 (Node runs the JavaScript actions in CI).
# Build: docker build --build-arg JDK=8 -t fairsim-build:jdk8-2 ci/fairsim-build
# The tag suffix is the Dockerfile revision, shared by all JDK variants.
# Bump it with every change here and update .forgejo/workflows/ci.yml.
ARG JDK=8

FROM node:24.22.2-bookworm-slim AS node

FROM maven:3.9.16-eclipse-temurin-${JDK}-noble
COPY --from=node /usr/local/bin/node /usr/local/bin/node
RUN java -version && mvn -v && node -v && git --version && curl --version | head -n 1
