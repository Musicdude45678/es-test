import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

/**
 * Release signing. Looks for, in order:
 *  1. REPS_KEYSTORE_PROPERTIES = path to a keystore.properties file (storeFile is resolved relative to it)
 *  2. keystore/keystore.properties in the repo root (gitignored)
 *  3. REPS_KEYSTORE_B64 + REPS_KEYSTORE_PASSWORD + REPS_KEY_ALIAS + REPS_KEY_PASSWORD (CI secrets)
 * If none is present the release build is signed with the debug key so it still installs.
 */
val keystoreProps = Properties()
var keystoreFile: File? = null
run {
    val candidates = listOfNotNull(System.getenv("REPS_KEYSTORE_PROPERTIES")?.let { File(it) }, rootProject.file("keystore/keystore.properties"))
    val propsFile = candidates.firstOrNull { it.isFile }
    if (propsFile != null) {
        propsFile.inputStream().use { keystoreProps.load(it) }
        keystoreProps.getProperty("storeFile")?.let { sf ->
            val f = File(sf)
            keystoreFile = if (f.isAbsolute) f else File(propsFile.parentFile, sf).takeIf { it.isFile } ?: rootProject.file(sf)
        }
    } else if (!System.getenv("REPS_KEYSTORE_B64").isNullOrBlank()) {
        val out = layout.buildDirectory.file("ci-release.jks").get().asFile
        out.parentFile.mkdirs()
        out.writeBytes(Base64.getDecoder().decode(System.getenv("REPS_KEYSTORE_B64").trim()))
        keystoreFile = out
        keystoreProps.setProperty("storePassword", System.getenv("REPS_KEYSTORE_PASSWORD") ?: "")
        keystoreProps.setProperty("keyAlias", System.getenv("REPS_KEY_ALIAS") ?: "reps")
        keystoreProps.setProperty("keyPassword", System.getenv("REPS_KEY_PASSWORD") ?: "")
    }
    Unit
}
val hasReleaseKey = keystoreFile?.isFile == true

android {
    namespace = "com.reps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reps.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "DebugProbesKt.bin")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
