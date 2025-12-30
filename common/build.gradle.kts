plugins {
    id("java")
}

dependencies {
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

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
