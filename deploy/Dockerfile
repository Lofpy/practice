# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:8-jdk AS build
WORKDIR /src
RUN apt-get update && apt-get install -y --no-install-recommends curl maven && rm -rf /var/lib/apt/lists/*
COPY . .
RUN mkdir -p runtime && curl --fail --location --retry 3 -o runtime/windspigot.jar https://github.com/Wind-Development/WindSpigot/releases/download/v2.1.3/WindSpigot-2.1.3.jar \
 && echo '53d8553521474035762f0652e5dab8f2c37007af0980eecce7a904d3aa84f484  runtime/windspigot.jar' | sha256sum -c - \
 && mkdir -p /via \
 && curl --fail --location --retry 3 -o /via/ViaVersion.jar https://github.com/ViaVersion/ViaVersion/releases/download/5.11.0/ViaVersion-5.11.0.jar \
 && curl --fail --location --retry 3 -o /via/ViaBackwards.jar https://github.com/ViaVersion/ViaBackwards/releases/download/5.11.0/ViaBackwards-5.11.0.jar \
 && curl --fail --location --retry 3 -o /via/ViaRewind.jar https://github.com/ViaVersion/ViaRewind/releases/download/4.1.3/ViaRewind-4.1.3.jar \
 && echo '18d19e90fc9467d68128c076630ae8700449c901402a3ef421837ce006bc8cae  /via/ViaVersion.jar' | sha256sum -c - \
 && echo 'b21983d561e3f92df257683f0133ab6c68ec68175e8acfd82c6231723bf83587  /via/ViaBackwards.jar' | sha256sum -c - \
 && echo '2d5970d22b4711c9ab2800932326c7b08acdace25ed7c6bbb8f6ea81054962b4  /via/ViaRewind.jar' | sha256sum -c - \
 && mvn --batch-mode verify && mvn --batch-mode -f lobby-plugin/pom.xml verify

FROM eclipse-temurin:17-jre AS pvp
WORKDIR /data
COPY --from=build /src/runtime/windspigot.jar /opt/poppy/windspigot.jar
COPY --from=build /src/target/PoppyPractice-0.1.0.jar /opt/poppy/plugins/PoppyPractice.jar
COPY --from=build /via/ /opt/poppy/plugins/
COPY server-template/ /opt/poppy/defaults/
COPY deploy/entrypoint.sh /usr/local/bin/poppy-entrypoint
ENV SERVER_KIND=pvp JAVA_MEMORY=4G SERVER_PORT=25566
EXPOSE 25566
ENTRYPOINT ["poppy-entrypoint"]

FROM eclipse-temurin:17-jre AS lobby
WORKDIR /data
COPY --from=build /src/runtime/windspigot.jar /opt/poppy/windspigot.jar
COPY --from=build /src/lobby-plugin/target/PoppyLobby-0.1.0.jar /opt/poppy/plugins/PoppyLobby.jar
COPY --from=build /via/ /opt/poppy/plugins/
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
