# syntax=docker/dockerfile:1.7
ARG BUILD_JAVA=eclipse-temurin:8-jdk
ARG BACKEND_JAVA=eclipse-temurin:17-jre
ARG PROXY_JAVA=eclipse-temurin:25-jre
FROM ${BUILD_JAVA} AS build
WORKDIR /src
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
COPY . .
RUN mkdir -p runtime && cp .build/windspigot.jar runtime/windspigot.jar \
 && mvn --batch-mode verify && mvn --batch-mode -f lobby-plugin/pom.xml verify

FROM ${BACKEND_JAVA} AS backend
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-yaml python3-toml \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/poppy/defaults /opt/poppy/plugins /data /run/poppy \
 && chown -R 10001:10001 /data /run/poppy
COPY --from=build /src/.build/windspigot.jar /opt/poppy/server.jar
COPY --from=build /src/.build/Via*.jar /opt/poppy/plugins/
COPY network-template/via-config.yml /opt/poppy/defaults/plugins/ViaVersion/config.yml
COPY deploy/runtime.py /opt/poppy/runtime.py
COPY --chmod=0755 deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
WORKDIR /data
USER 10001:10001
ENTRYPOINT ["poppy-entrypoint"]

FROM backend AS pvp
COPY --from=build /src/target/PoppyPractice-0.1.0.jar /opt/poppy/plugins/PoppyPractice.jar
COPY --from=build /src/lobby-plugin/target/PoppyLobby-0.1.0.jar /opt/poppy/plugins/PoppyLobby.jar
COPY server-template/*.yml server-template/server.properties server-template/eula.txt /opt/poppy/defaults/
COPY network-template/lobby/spigot.yml /opt/poppy/defaults/spigot.yml
COPY network-template/pvp-lobby.yml /opt/poppy/defaults/plugins/PoppyLobby/config.yml
ENV SERVER_KIND=pvp JAVA_MEMORY=4G SERVER_PORT=25566

FROM backend AS lobby
COPY --from=build /src/lobby-plugin/target/PoppyLobby-0.1.0.jar /opt/poppy/plugins/PoppyLobby.jar
COPY network-template/lobby/*.yml network-template/lobby/server.properties /opt/poppy/defaults/
COPY server-template/eula.txt /opt/poppy/defaults/eula.txt
ENV SERVER_KIND=lobby JAVA_MEMORY=1G SERVER_PORT=25567

FROM ${PROXY_JAVA} AS proxy
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-yaml python3-toml \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/poppy/defaults /data /run/poppy \
 && chown -R 10001:10001 /data /run/poppy
COPY .build/velocity.jar /opt/poppy/server.jar
COPY network-template/proxy/velocity.toml /opt/poppy/defaults/velocity.toml
COPY deploy/runtime.py /opt/poppy/runtime.py
COPY --chmod=0755 deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
WORKDIR /data
USER 10001:10001
ENV SERVER_KIND=proxy JAVA_MEMORY=512M SERVER_PORT=25565
ENTRYPOINT ["poppy-entrypoint"]
