plugins {
    id("java-library")
}

dependencies {
    // Google Guava for Preconditions
    api("com.google.guava:guava:33.0.0-jre")

    // Adventure API for text components
    api("net.kyori:adventure-api:4.15.0")
    api("net.kyori:adventure-text-serializer-gson:4.15.0")
    api("net.kyori:adventure-text-serializer-plain:4.15.0")

    // ONNX Runtime for local embeddings
    api("com.microsoft.onnxruntime:onnxruntime:1.19.2")

    // DJL for BERT tokenization
    api("ai.djl:api:0.30.0")
    api("ai.djl.huggingface:tokenizers:0.30.0")

    // HTTP client for external embedding APIs
    api("com.squareup.okhttp3:okhttp:4.12.0")

    // HikariCP for database connection pooling
    api("com.zaxxer:HikariCP:5.1.0")

    // PostgreSQL driver (for Supabase)
    api("org.postgresql:postgresql:42.7.4")

    // JVector for vector similarity search
    api("io.github.jbellis:jvector:4.0.0-rc.6")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
