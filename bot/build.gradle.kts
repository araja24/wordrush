// Command-line bot: plays WordRush over the same REST + STOMP protocol as the browser.
// Doubles as a load generator (Stage 10). Uses Spring's messaging/WebSocket client libraries,
// not the Spring Boot runtime; the Boot plugin is only used to build an executable jar.
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "dev.wordrush"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":game-core"))
    implementation("org.springframework:spring-messaging")
    implementation("org.springframework:spring-websocket")
    implementation("org.apache.tomcat.embed:tomcat-embed-websocket") // JSR-356 WebSocket client
    implementation("tools.jackson.core:jackson-databind")
}

springBoot {
    mainClass = "dev.wordrush.botcli.BotMain"
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "wordrush-bot.jar"
}
