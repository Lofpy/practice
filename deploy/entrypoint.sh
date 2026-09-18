#!/bin/sh
set -eu
mkdir -p /data/plugins
if [ -d /opt/poppy/defaults ]; then cp -an /opt/poppy/defaults/. /data/; fi
if [ "$SERVER_KIND" = pvp ] || [ "$SERVER_KIND" = lobby ]; then
  cp /opt/poppy/windspigot.jar /data/windspigot.jar
  cp /opt/poppy/plugins/*.jar /data/plugins/
  if [ ! -f /data/eula.txt ]; then
    echo 'eula=false' > /data/eula.txt
    echo 'Review the Minecraft EULA, then set eula=true in the persistent data directory.' >&2
    exit 1
  fi
  if [ "$SERVER_KIND" = pvp ]; then
    sed -i 's/^server-port=.*/server-port=25566/; s/^server-ip=.*/server-ip=127.0.0.1/; s/^online-mode=.*/online-mode=false/' /data/server.properties
  fi
  exec java -Xms512M "-Xmx${JAVA_MEMORY}" -XX:+UseG1GC -jar windspigot.jar nogui
fi
cp /opt/poppy/velocity.jar /data/velocity.jar
exec java -Xms256M "-Xmx${JAVA_MEMORY}" -jar velocity.jar

