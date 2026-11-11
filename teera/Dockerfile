# The Teera room server, ready for any host that runs containers (Render, Fly.io,
# Railway, a VPS). The Android app is left out of the build on its own, because there
# is no Android SDK in the image.
#
#   docker build -t teera-server .
#   docker run -p 8080:8080 teera-server

FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY . .
ENV TEERA_SERVER_ONLY=1
RUN gradle :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
ENV PORT=8080
# Free hosts give the container about half a gigabyte; keep the JVM well inside it.
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m"
EXPOSE 8080
CMD ["/app/bin/server"]
