import java.net.URI

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val sourceCommit = providers.gradleProperty("baroSourceCommit").orElse(providers.exec { workingDir(rootProject.projectDir.parentFile); isIgnoreExitValue = true; commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.map { it.trim().ifEmpty { "unversioned" } }).get()
val appVersionCode = providers.gradleProperty("baroVersionCode").orElse("20").get().toInt()
val appVersionName = providers.gradleProperty("baroVersionName").orElse("0.10.0").get()
val backendOrigin = providers.gradleProperty("baroBackendUrl").orElse("").get()
if (backendOrigin.isNotEmpty()) {
    val origin = URI(backendOrigin)
    require(origin.scheme in listOf("https", "http") && origin.host != null && origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null) { "baroBackendUrl must be an HTTP(S) origin without credentials, query or fragment" }
}
val feedbackOrigin = providers.gradleProperty("baroFeedbackUrl").orElse("https://baro-feedback-dpan538s-projects.vercel.app").get().trimEnd('/')
if (feedbackOrigin.isNotEmpty()) {
    val origin = URI(feedbackOrigin)
    require((origin.scheme == "https" || origin.scheme == "http" && origin.host in listOf("127.0.0.1", "10.0.2.2", "localhost")) && origin.host != null && origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null && origin.rawPath in listOf("", "/")) { "baroFeedbackUrl must be a dedicated HTTPS origin without credentials, query, fragment or path" }
}
val identityDirectory = layout.buildDirectory.dir("generated/baroIdentity")
val writeBuildIdentity by tasks.registering {
    inputs.property("sourceCommit", sourceCommit)
    inputs.property("versionCode", appVersionCode)
    inputs.property("versionName", appVersionName)
    inputs.property("backendOrigin", backendOrigin)
    inputs.property("feedbackOrigin", feedbackOrigin)
    outputs.dir(identityDirectory)
    doLast {
        val target = identityDirectory.get().file("build-identity.json").asFile
        target.parentFile.mkdirs()
        target.writeText(groovy.json.JsonOutput.toJson(mapOf("sourceCommit" to sourceCommit, "packageName" to "studio.sundown.weather", "versionCode" to appVersionCode, "versionName" to appVersionName, "backendOrigin" to backendOrigin, "feedbackOrigin" to feedbackOrigin)))
    }
}
android {
    namespace = "studio.sundown.weather"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.sundown.weather"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SOURCE_COMMIT", groovy.json.JsonOutput.toJson(sourceCommit))
        buildConfigField("String", "BACKEND_URL", groovy.json.JsonOutput.toJson(backendOrigin))
        buildConfigField("String", "FEEDBACK_URL", groovy.json.JsonOutput.toJson(feedbackOrigin))
    }
    sourceSets["main"].assets.srcDir(identityDirectory)
    buildFeatures { buildConfig = true }
    testBuildType = providers.gradleProperty("baroTestBuildType").orElse("debug").get()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        getByName("debug") { storeFile = file("../.tooling/baro-debug.keystore") }
        if (System.getenv("BARO_KEYSTORE") != null) create("production") {
            storeFile = file(System.getenv("BARO_KEYSTORE"))
            storePassword = System.getenv("BARO_STORE_PASSWORD")
            keyAlias = System.getenv("BARO_KEY_ALIAS")
            keyPassword = System.getenv("BARO_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { manifestPlaceholders["baroCleartext"] = "true" }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["baroCleartext"] = "false"
            if (signingConfigs.findByName("production") != null) signingConfig = signingConfigs.getByName("production")
        }
        create("preview") {
            initWith(getByName("release"))
            // Keep the dedicated BARO test identity across previews so Android
            // upgrades the installed app and retains its private data.
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("com.android.tools.build:apksig:8.13.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("junit:junit:4.13.2")
}

tasks.named("preBuild") { dependsOn(writeBuildIdentity) }
