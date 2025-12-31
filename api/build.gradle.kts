plugins {
    id("java")
}

dependencies {
    // Pure Java - no platform dependencies
    implementation(platform("net.kyori:adventure-bom:4.17.0"))
    implementation("net.kyori:adventure-api")
    implementation("com.google.guava:guava:33.0.0-jre")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
