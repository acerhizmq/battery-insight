import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        FileInputStream(versionPropsFile).use { load(it) }
    }
}

fun bumpVersionName(current: String): String {
    val parts = current.split(".").toMutableList()
    if (parts.isNotEmpty()) {
        val lastIdx = parts.size - 1
        val lastNum = parts[lastIdx].toIntOrNull() ?: 0
        parts[lastIdx] = (lastNum + 1).toString()
        return parts.joinToString(".")
    }
    return "1.0.1"
}

var currentVersionCode = versionProps.getProperty("versionCode", "25").toInt()
var currentVersionName = versionProps.getProperty("versionName", "1.3.0")

// Only release packaging bumps the version; debug builds, IDE syncs and helper tasks
// (e.g. buildEnvironment) must not burn version numbers.
val releaseTaskPattern = Regex("^(assemble|bundle|package)\\w*Release$", RegexOption.IGNORE_CASE)
val isBuildingRelease = gradle.startParameter.taskNames.any { taskName ->
    val name = taskName.substringAfterLast(':')
    releaseTaskPattern.matches(name) || name in setOf("assemble", "bundle", "build")
}

val alreadyBumped = rootProject.extra.has("versionBumped")
if (isBuildingRelease && !alreadyBumped) {
    currentVersionCode += 1
    currentVersionName = bumpVersionName(currentVersionName)

    // Written by hand with LF line endings: Properties.store() uses the platform separator,
    // which produced CRLF-only diffs between Windows and Linux checkouts.
    versionPropsFile.writeText(
        "#Auto-incremented on build\n" +
            "versionName=$currentVersionName\n" +
            "versionCode=$currentVersionCode\n"
    )
    rootProject.extra.set("versionBumped", true)
    println("==> Auto-incremented Build Version: $currentVersionName (Code: $currentVersionCode)")
}

// Optional release signing: keystore.properties (git-ignored) or BI_* environment variables.
// Without them, release builds fall back to the debug key as before.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = signingValue("storeFile", "BI_STORE_FILE")

android {
    namespace = "com.acer.batteryinsight"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.acer.batteryinsight"
        minSdk = 29
        targetSdk = 37
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "BI_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "BI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "BI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                println("==> No release keystore configured; signing release with the debug key")
                signingConfigs.getByName("debug")
            }
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
        aidl = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.buildType.name == "release") {
                output?.outputFileName = "BatteryInsight-v${variant.versionName}-Universal.apk"
            } else {
                output?.outputFileName = "BatteryInsight-v${variant.versionName}-Debug.apk"
            }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apkFile = releaseDir.listFiles()?.firstOrNull { it.name.startsWith("BatteryInsight") && it.name.endsWith(".apk") }
        if (apkFile != null && apkFile.exists()) {
            rootProject.projectDir.listFiles()?.filter {
                it.name.startsWith("BatteryInsight-") && it.name.endsWith("-Universal.apk") && it.name != apkFile.name
            }?.forEach { it.delete() }

            val destFile = File(rootProject.projectDir, apkFile.name)
            apkFile.copyTo(destFile, overwrite = true)
            println("==> Copied ${apkFile.name} to ${destFile.absolutePath}")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // LibSU (TopJohnWu - Official Magisk / KernelSU root library)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")

    testImplementation("junit:junit:4.13.2")
}
