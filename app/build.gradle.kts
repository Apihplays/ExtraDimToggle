import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing credentials live in gitignored keystore.properties.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.extradim.toggle"
    compileSdk = 37 // Android 17

    defaultConfig {
        applicationId = "com.extradim.toggle"
        minSdk = 26
        targetSdk = 37 // Android 17
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = keystoreProps["storeFile"]?.let { file(it) }
                storePassword = keystoreProps["storePassword"] as String?
                keyAlias = keystoreProps["keyAlias"] as String?
                keyPassword = keystoreProps["keyPassword"] as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// KernelSU module packaging: stage module/ files + release APK, then zip them
// into a flashable module (app/build/distributions/extradim-toggle-module.zip).
val moduleDir = rootProject.file("module")

val releaseApk = layout.buildDirectory.file(
    "outputs/apk/release/app-release.apk"
)

val prepareKsuModule by tasks.registering(Copy::class) {
    dependsOn(tasks.named("assembleRelease"))
    from(moduleDir) {
        include("module.prop", "customize.sh", "service.sh", "uninstall.sh")
    }
    from(releaseApk) {
        rename { "ExtraDimToggle.apk" }
    }
    into(layout.buildDirectory.dir("ksuModule/staged"))
}

val buildKsuModule by tasks.registering(Zip::class) {
    group = "build"
    description = "Packages the KernelSU module (module files + release APK) into a flashable zip."
    dependsOn(prepareKsuModule)
    archiveFileName.set("extradim-toggle-module.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(moduleDir) {
        include("module.prop", "customize.sh", "service.sh", "uninstall.sh")
        eachFile {
            if (name.endsWith(".sh")) permissions { unix("0755") }
        }
    }
    from(prepareKsuModule.map { it.destinationDir }) {
        include("ExtraDimToggle.apk")
    }

    doLast {
        // Sanity check: every required entry must exist in the zip.
        ZipFile(outputs.files.singleFile).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.toSet()
            listOf(
                "module.prop", "customize.sh", "service.sh", "uninstall.sh", "ExtraDimToggle.apk"
            ).forEach { required ->
                if (required !in names) throw GradleException("Missing entry in module zip: $required")
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
