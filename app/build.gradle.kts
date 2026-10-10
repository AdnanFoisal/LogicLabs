plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.logiclabs.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.logiclabs.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // R8 full mode per the project rules: shrunk, optimized, obfuscated, with
            // explicit keeps for the serialization models in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-digital"))
    implementation(project(":core-bridge"))
    implementation(project(":core-data"))
    implementation(project(":core-designsystem"))
    implementation(project(":feature-breadboard"))
    implementation(project(":feature-instruments"))
    implementation(project(":feature-tools"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Version-less: resolved by the Compose BOM above. LazyColumn/FlowRow/clickable and
    // AnimatedVisibility were previously arriving only transitively through material3.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation(libs.kotlinx.coroutines.android)
    // NavHost for the splash → home → bench → settings shell. Versioned here rather
    // than through the Compose BOM because navigation is not part of that BOM.
    implementation(libs.androidx.navigation.compose)
    // Android 12+ branded cold-start splash with a pre-31 compat path.
    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
