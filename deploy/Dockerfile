# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:8-jdk AS build
WORKDIR /src
RUN apt-get update && apt-get install -y --no-install-recommends curl maven && rm -rf /var/lib/apt/lists/*
COPY . .
RUN mkdir -p runtime && curl --fail --location --retry 3 -o runtime/windspigot.jar https://github.com/Wind-Development/WindSpigot/releases/download/v2.1.3/WindSpigot-2.1.3.jar \
 && echo '53d8553521474035762f0652e5dab8f2c37007af0980eecce7a904d3aa84f484  runtime/windspigot.jar' | sha256sum -c - \
 && mvn --batch-mode verify && mvn --batch-mode -f lobby-plugin/pom.xml verify

FROM eclipse-temurin:17-jre AS pvp
WORKDIR /data
COPY --from=build /src/runtime/windspigot.jar /opt/poppy/windspigot.jar
COPY --from=build /src/target/PoppyPractice-0.1.0.jar /opt/poppy/plugins/PoppyPractice.jar
COPY server-template/ /opt/poppy/defaults/
COPY deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
ENV SERVER_KIND=pvp JAVA_MEMORY=4G SERVER_PORT=25566
EXPOSE 25566
ENTRYPOINT ["poppy-entrypoint"]

FROM eclipse-temurin:17-jre AS lobby
WORKDIR /data
COPY --from=build /src/runtime/windspigot.jar /opt/poppy/windspigot.jar
COPY --from=build /src/lobby-plugin/target/PoppyLobby-0.1.0.jar /opt/poppy/plugins/PoppyLobby.jar
COPY network-template/lobby/ /opt/poppy/defaults/
COPY deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
ENV SERVER_KIND=lobby JAVA_MEMORY=1G SERVER_PORT=25567
EXPOSE 25567
ENTRYPOINT ["poppy-entrypoint"]

FROM eclipse-temurin:25-jre AS proxy
WORKDIR /data
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && curl --fail --location --retry 3 -o /opt/poppy/velocity.jar https://fill-data.papermc.io/v1/objects/846411d2d0560fed0f23496ffb89681be528d2c0650ecdcf21724d2d7bd9c1ee/velocity-4.1.1-24.jar \
 && echo '846411d2d0560fed0f23496ffb89681be528d2c0650ecdcf21724d2d7bd9c1ee  /opt/poppy/velocity.jar' | sha256sum -c -
COPY network-template/proxy/velocity.toml /opt/poppy/defaults/velocity.toml
COPY deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
ENV SERVER_KIND=proxy JAVA_MEMORY=512M SERVER_PORT=25565
EXPOSE 25565
ENTRYPOINT ["poppy-entrypoint"]

