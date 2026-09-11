plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.logiclabs.feature.tools"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-digital"))
    implementation(project(":core-bridge"))
    implementation(project(":core-designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    // LazyRow in the probe picker + animateFloatAsState in the vector sweep.
    // Versions come from the Compose BOM above, so no version string here.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    // androidx.core.content.FileProvider, for the exported certificate PDF Uri.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
