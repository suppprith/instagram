plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionName = providers.gradleProperty("dms.versionName").get()

/** 1.2.3 -> 10203. Tags drive the version, so versionCode always grows with versionName. */
fun versionCodeOf(name: String): Int {
    val parts = name.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val (major, minor, patch) = (parts + listOf(0, 0, 0)).take(3)
    return major * 10_000 + minor * 100 + patch
}

fun String.quoted() = "\"" + replace("\"", "\\\"") + "\""

android {
    namespace = "com.suppprith.dms"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suppprith.dms"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName

        buildConfigField("String", "PATCH_URL", providers.gradleProperty("dms.patchUrl").get().quoted())
        buildConfigField("String", "RELEASES_REPO", providers.gradleProperty("dms.releasesRepo").get().quoted())
    }

    // Release signing comes from the environment (GitHub Actions secrets). Without it,
    // assembleRelease produces an unsigned APK and the release workflow refuses to publish.
    val keystorePath = System.getenv("DMS_KEYSTORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DMS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DMS_KEY_ALIAS")
                keyPassword = System.getenv("DMS_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        // Version bumps are handled deliberately, not by lint.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
