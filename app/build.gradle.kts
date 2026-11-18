plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vaultview.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vaultview.app"
        minSdk = 26
        targetSdk = 34
        // CI passes the GitHub Actions run number so every pushed build is a newer version.
        val build = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionCode = build
        versionName = "1.0.$build"
        buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("UPDATE_REPO") ?: (project.findProperty("updateRepo") ?: "")}\"")
    }

    // One fixed signing key for local and CI builds, so updates install over each other.
    val keystoreFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) } ?: rootProject.file("release.keystore")
    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: project.findProperty("signingStorePassword") as String?
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: (project.findProperty("signingKeyAlias") as String? ?: "vaultview")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: project.findProperty("signingKeyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES") } }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
