# fairSIM build image: JDK 8, Maven 3.9, Node 24 (Node runs the JavaScript actions in CI).
# Build: docker build -t fairsim-build:jdk8-1 ci/fairsim-build
# Bump the tag with every change here and update .forgejo/workflows/ci.yml.
FROM node:24.22.2-bookworm-slim AS node

FROM maven:3.9.16-eclipse-temurin-8-noble
COPY --from=node /usr/local/bin/node /usr/local/bin/node
RUN java -version && mvn -v && node -v && git --version && curl --version | head -n 1
