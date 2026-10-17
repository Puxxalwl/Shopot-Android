plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
}

val releaseStore = providers.environmentVariable("STILLE_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("STILLE_STORE_PASSWORD")
val releaseAlias = providers.environmentVariable("STILLE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("STILLE_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
    .all { it.orNull?.isNotBlank() == true }

android {
    namespace = "com.stille.messenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stille.messenger"
        minSdk = 26
        targetSdk = 35
        // Increase for published releases to support ordered updates and avoid downgrades.
        versionCode = 25
        versionName = "0.2.5"

        testInstrumentationRunner = "com.stille.messenger.HiltTestRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.0"
    }

    packagingOptions {
        jniLibs {
            // ProcessBuilder needs a real executable file, not a ZIP entry.
            useLegacyPackaging = true
            keepDebugSymbols += "**/libshopot.so"
        }
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DISCLAIMER.txt",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt"
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("stableRelease") {
                storeFile = file(releaseStore.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DEBUG_LOG", "true")
            buildConfigField("boolean", "DEBUG_CORE", "false")
        }
        release {
            buildConfigField("boolean", "DEBUG_LOG", "false")
            buildConfigField("boolean", "DEBUG_CORE", "false")
            // R8 minification is deferred until keep rules for Hilt/Compose are tuned.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Never silently sign releases with a runner-generated debug key.
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("stableRelease")
        }
    }

    lint {
        // Release APK packaging does not gate on lint; CI runs lint separately.
        checkReleaseBuilds = false
    }
}

val verifyReleaseSigning by tasks.registering {
    val configured = releaseSigningReady
    doLast {
        check(configured) {
            "Release signing is required: configure STILLE_KEYSTORE_PATH, STILLE_STORE_PASSWORD, " +
                "STILLE_KEY_ALIAS and STILLE_KEY_PASSWORD. Reuse the existing signing key."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

// Keep compile-only checks lightweight, but never produce an APK without its core.
val verifyCoreBinary by tasks.registering {
    val binary = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libshopot.so")
    inputs.file(binary).optional()
    doLast {
        val file = binary.asFile
        check(file.isFile && file.length() > 64) {
            "Android core is missing. Run: bash tools/build-core.sh"
        }
        val header = file.inputStream().use { it.readNBytes(20) }
        check(header[0] == 0x7f.toByte() && header[1] == 69.toByte() &&
            header[2] == 76.toByte() && header[3] == 70.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()) {
            "Android core must be a little-endian AArch64 ELF executable"
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("NativeLibs") }
    .configureEach { dependsOn(verifyCoreBinary) }

dependencies {
    implementation(project(":data"))

    // Core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")

    // Compose
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation("androidx.compose.ui:ui-text:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    implementation("com.google.dagger:dagger:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Coil (image loading — no Firebase)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Offline QR encoding/decoding only; no network SDK or crypto involvement.
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
}
