plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    // JitPack root POM is an empty aggregator with broken self-transitives; depend on the extractor submodule (real classes).
    implementation("com.github.teamnewpipe.NewPipeExtractor:extractor:d59dc216f49290b2c6c8cf532378e2ee42b32d4a")
    testImplementation(libs.junit)
}