plugins {
    id("java")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("arksic.App")
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Format decoding ---
    // WAV needs nothing: javax.sound.sampled reads it natively.
    //
    // MP3 and FLAC are added as runtime-only. Neither is referenced by
    // name anywhere in our source — each registers itself as a
    // javax.sound.sampled SPI provider (META-INF/services) so
    // AudioSystem.getAudioInputStream(...) decodes MP3/FLAC the same way
    // it already decodes WAV. See docs/foundation/architecture.md
    // ("Stack") for why this is load-bearing on the decoder abstraction.
    runtimeOnly("com.googlecode.soundlibs:mp3spi:1.9.5.4")      // JLayer, fronted by mp3spi
    runtimeOnly("org.jflac:jflac-codec:1.5.2")                   // jflac-codec

    // --- Tagging ---
    // The one place we *do* compile against a named library, per
    // design-notes.md ("a dedicated tag-reading library ... not
    // hand-rolled parsing").
    implementation("net.jthink:jaudiotagger:3.0.1")

    // --- Testing ---
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
