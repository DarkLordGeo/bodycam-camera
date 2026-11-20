plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// -Pscreenshots=true enables Robolectric/Roborazzi screenshot tests (kept out of the main build).
val screenshots = providers.gradleProperty("screenshots").isPresent
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.darklordgeo.bodycamfx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darklordgeo.bodycamfx"
        minSdk = 29
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "0.1.$ciBuildNumber"
    }

    // A committed development key so every CI build installs over the previous one.
    // Replace with a private key before publishing to a store.
    signingConfigs {
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "bodycamfx"
            keyAlias = "dev"
            keyPassword = "bodycamfx"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
        }
        debug {
            signingConfig = signingConfigs.getByName("dev")
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = screenshots
        unitTests.all {
            if (screenshots) it.systemProperty("roborazzi.test.record", "true")
        }
    }
    sourceSets {
        getByName("test") {
            if (screenshots) java.srcDir("src/screenshotTest/java")
        }
    }
}

dependencies {
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    implementation("dev.chrisbanes.haze:haze:1.2.2")

    testImplementation("junit:junit:4.13.2")
    if (screenshots) {
        testImplementation("org.robolectric:robolectric:4.14.1")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.44.0-alpha02")
        testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.44.0-alpha02")
        testImplementation("androidx.compose.ui:ui-test-junit4")
        testImplementation("androidx.test:core:1.6.1")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
}
