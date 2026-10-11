plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.deckos.keyboard"
 compileSdk = 35
 signingConfigs {
  create("stableDebug") {
   storeFile = rootProject.file("app/deck-keyboard.p12")
   storePassword = System.getenv("DECK_KEYSTORE_PASSWORD") ?: ""
   keyAlias = "deck-keyboard"
   keyPassword = System.getenv("DECK_KEYSTORE_PASSWORD") ?: ""
   storeType = "PKCS12"
  }
 }
 buildTypes {
  getByName("debug") {
   if (System.getenv("DECK_KEYSTORE_PASSWORD") != null) {
    signingConfig = signingConfigs.getByName("stableDebug")
   }
  }
 }
 defaultConfig { applicationId = "com.deckos.keyboard"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.4.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
