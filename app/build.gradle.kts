import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.process.ExecOperations


private const val PDFCRAFT_REVISION = "6873a87e0acecae61c0ac02422deb99e890abdf7"
private const val PDFCRAFT_MIN_API = 29

abstract class PreparePdfCraftSource : DefaultTask() {
  @get:Inject abstract val execOperations: ExecOperations
  @get:Input abstract val revision: Property<String>
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction fun checkout() {
    val dir = outputDir.get().asFile
    if (!File(dir, ".git").isDirectory) {
      dir.deleteRecursively()
      execOperations.exec {
        commandLine("git", "clone", "--filter=blob:none", "https://github.com/artcraft-android/pdfcraft-android.git", dir.absolutePath)
      }
    }
    execOperations.exec {
      workingDir(dir)
      commandLine("git", "fetch", "--depth", "1", "origin", revision.get())
    }
    execOperations.exec {
      workingDir(dir)
      commandLine("git", "checkout", "--detach", revision.get())
    }
  }
}

abstract class PdfCraftCargoBuild : DefaultTask() {
  @get:Inject abstract val execOperations: ExecOperations
  @get:Input abstract val cargo: Property<String>
  @get:Input abstract val workspacePath: Property<String>
  @get:Input abstract val ndkPath: Property<String>
  @get:Input abstract val abis: ListProperty<String>
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction fun buildNative() {
    val targets = abis.get().flatMap { listOf("-t", it) }
    val out = outputDir.get().asFile.absolutePath
    execOperations.exec {
      workingDir(workspacePath.get())
      environment("ANDROID_NDK_HOME", ndkPath.get())
      commandLine(
        listOf(cargo.get(), "ndk") + targets +
          listOf("--platform", PDFCRAFT_MIN_API.toString(), "-o", out, "build", "--release", "--locked", "-p", "pdfcraft-android", "--lib")
      )
    }
    File(out).walk().filter { it.isFile && it.name != "libpdfcraft_android.so" }.forEach { it.delete() }
  }
}

abstract class PdfCraftUniffiBindgen : DefaultTask() {
  @get:Inject abstract val execOperations: ExecOperations
  @get:Input abstract val cargo: Property<String>
  @get:Input abstract val workspacePath: Property<String>
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val library: RegularFileProperty
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction fun generateBindings() {
    val out = outputDir.get().asFile
    out.deleteRecursively()
    out.mkdirs()
    execOperations.exec {
      workingDir(workspacePath.get())
      commandLine(
        cargo.get(), "run", "--quiet", "--locked", "-p", "pdfcraft-android-bindgen", "--",
        "generate", library.get().asFile.absolutePath, "--language", "kotlin", "--out-dir", out.absolutePath, "--no-format"
      )
    }
  }
}

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
  ndkVersion = "29.0.14206865"

  defaultConfig {
    applicationId = "com.habib.advanceeditor"
    minSdk = 26
    ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64")) }
    targetSdk = 36
    versionCode = 7
    versionName = "1.6"

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
  // PdfCraft (MIT OR Apache-2.0) native paragraph editor; UniFFI uses JNA.
  implementation("net.java.dev.jna:jna:5.19.1@aar")
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



val pdfCraftSource = tasks.register<PreparePdfCraftSource>("preparePdfCraftSource") {
  revision.set(PDFCRAFT_REVISION)
  outputDir.set(layout.buildDirectory.dir("pdfcraft-source"))
  outputs.upToDateWhen { false }
}

androidComponents {
  onVariants { variant ->
    val variantName = variant.name.replaceFirstChar { it.uppercase() }
    val cargoBuild = tasks.register<PdfCraftCargoBuild>("buildPdfCraftNative$variantName") {
      cargo.set("cargo")
      workspacePath.set(pdfCraftSource.flatMap { it.outputDir }.map { it.asFile.absolutePath })
      ndkPath.set(sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
      abis.set(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
      outputDir.set(layout.buildDirectory.dir("generated/pdfcraft/jniLibs/${variant.name}"))
      dependsOn(pdfCraftSource)
      outputs.upToDateWhen { false }
    }
    val bindgen = tasks.register<PdfCraftUniffiBindgen>("generatePdfCraftKotlin$variantName") {
      cargo.set("cargo")
      workspacePath.set(pdfCraftSource.flatMap { it.outputDir }.map { it.asFile.absolutePath })
      library.set(cargoBuild.flatMap { it.outputDir.file("arm64-v8a/libpdfcraft_android.so") })
      outputDir.set(layout.buildDirectory.dir("generated/pdfcraft/kotlin/${variant.name}"))
    }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoBuild, PdfCraftCargoBuild::outputDir)
    variant.sources.java?.addGeneratedSourceDirectory(bindgen, PdfCraftUniffiBindgen::outputDir)
  }
}

// Download pinned official tessdata_fast models so the installed APK works offline.
val ocrModelSpecs = listOf(
  Triple("ben", "c300ca8fbf0543f2fdd3256dbd5a203f404b513d", 855841L),
  Triple("eng", "31abd495e0f719db4f524c447e9d855124a0b0d6", 4113088L)
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
