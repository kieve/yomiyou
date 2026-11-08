import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "ca.kieve.yomiyou"
    compileSdk = 37

    defaultConfig {
        applicationId = "ca.kieve.yomiyou"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += listOf("epub", "zip")
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log etc. become no-ops on the JVM
        unitTests.all { it.useJUnitPlatform() }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Debug builds carry the test book: yomiage's output folder for one book (`<name>.yomiage.epub` and
 * `<name>.<voice>.voice.zip` files), named by `yomiyou.testBook` in local.properties, lands in the assets' `testbook/`
 * as `book.epub` and `voice-<voice>.zip`. Books are copyrighted and this repo is public: they stay out of it.
 */
abstract class BundleTestBook : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun bundle() {
        val target = output.get().asFile.resolve("testbook")
        target.deleteRecursively()
        target.mkdirs()
        for (file in source.orNull?.asFile?.listFiles().orEmpty()) {
            val name = when {
                file.name.endsWith(".yomiage.epub") -> "book.epub"
                file.name.endsWith(".voice.zip") ->
                    "voice-" + file.name.removeSuffix(".voice.zip").substringAfterLast('.') + ".zip"
                else -> continue
            }
            file.copyTo(target.resolve(name))
        }
    }
}

val testBook: String? = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }.getProperty("yomiyou.testBook")
}

val bundleTestBook = tasks.register<BundleTestBook>("bundleTestBook") {
    testBook?.let { source.set(file(it)) }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTestBook, BundleTestBook::output)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.jsoup) // Requesting and parsing HTML
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.markdown)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.jackson.kotlin)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
