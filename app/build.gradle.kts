import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.habib.advanceeditor"
    minSdk = 26
    targetSdk = 36
    versionCode = 5
    versionName = "1.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
      keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "habib-advance-release"
      keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // ML Kit Latin OCR used by rich PDF-to-DOCX extraction.
  implementation(libs.mlkit.text.recognition)
  // Task.await() bridge for Google Play Services asynchronous APIs.
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // Tesseract 5 offline OCR (Apache-2.0); Bengali and English models are packaged at build time.
  implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
  // Apache-2.0 PDF object and page manipulation engine.
  implementation("com.tom-roush:pdfbox-android:2.0.27.0")
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}


// Download pinned official tessdata_fast models so the installed APK works offline.
val ocrModelSpecs = listOf(
  Triple("ben", "c300ca8fbf0543f2fdd3256dbd5a203f404b513d", 855841L),
  Triple("eng", "bbef4675053b5b468cdb477053e28b1c698ba08e", 4113088L)
)
val downloadOcrLanguageModels = tasks.register("downloadOcrLanguageModels") {
  val modelDirectory = layout.projectDirectory.dir("src/main/assets/tessdata")
  outputs.files(ocrModelSpecs.map { modelDirectory.file("${it.first}.traineddata") })
  doLast {
    val directory = modelDirectory.asFile.apply { mkdirs() }
    ocrModelSpecs.forEach { (language, expectedSha1, expectedSize) ->
      val target = File(directory, "$language.traineddata")
      fun sha1Of(file: File): String =
        MessageDigest.getInstance("SHA-1")
          .digest(file.readBytes())
          .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

      // Never trust a cached model based on file size alone: same-size corruption
      // must trigger a fresh download before the assets are packaged.
      val existingIsValid = target.isFile &&
        target.length() == expectedSize &&
        sha1Of(target) == expectedSha1
      if (!existingIsValid) {
        val temporary = File(directory, "$language.traineddata.download")
        temporary.delete()
        val modelUrl = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/$language.traineddata"
        URL(modelUrl).openConnection().apply {
          connectTimeout = 20000
          readTimeout = 60000
        }.getInputStream().use { input ->
          temporary.outputStream().use { output -> input.copyTo(output) }
        }
        val downloadedSize = temporary.length()
        check(downloadedSize == expectedSize) {
          temporary.delete()
          "Downloaded $language OCR model has an unexpected size: $downloadedSize"
        }
        val downloadedSha1 = sha1Of(temporary)
        check(downloadedSha1 == expectedSha1) {
          temporary.delete()
          "Downloaded $language OCR model failed SHA-1 integrity verification: $downloadedSha1"
        }
        check(temporary.renameTo(target)) { "Unable to install $language OCR model." }
      }
    }
  }
}
tasks.named("preBuild").configure {
  dependsOn(downloadOcrLanguageModels)
}
