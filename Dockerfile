# ---- Build stage: compile and package the app ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ---- Runtime stage: slim JRE + native Tesseract/Leptonica so Tess4J (JNA) can actually load them.
# The tess4j jar only bundles Windows DLLs — on Linux it needs the OS's own shared libraries.
# eng.traineddata itself is handled separately (build.gradle's downloadTessdata task / the app's
# own startup fallback in TesseractReceiptOcrEngine), so no language-pack apt package is needed here.
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/finzin-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8585
ENTRYPOINT ["java", "-jar", "app.jar"]
