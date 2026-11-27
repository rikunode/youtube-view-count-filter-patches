extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.rikunode.youtubeviewcount.extension"
}

// The normal build retains the original filter extension byte-for-byte.
if (providers.gradleProperty("diagnosticBundle").orNull == "true") {
    android.sourceSets.getByName("main").java.setSrcDirs(listOf("src/diagnostic/java"))
}
