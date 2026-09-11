plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.logiclabs.core.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Decoupled during early milestone implementation to ensure progressive testability
    // implementation(project(":core-digital"))
    // implementation(project(":core-bridge"))
    implementation(libs.junit)
    testImplementation(libs.junit)
}
