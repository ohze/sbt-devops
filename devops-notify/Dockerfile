FROM eclipse-temurin:17-alpine as build

RUN apk --update add binutils

RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

FROM alpine:3.14

ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:$PATH"

COPY --from=build /javaruntime $JAVA_HOME
COPY target/notify.jar /
RUN echo Verifying install ... \
    && echo java --version && java --version \
    && echo Complete.

ENTRYPOINT [ "java", "-Dhttps.protocols=TLSv1.2", "-jar", "/notify.jar" ]
