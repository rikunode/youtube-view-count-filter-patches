group = "app.rikunode.youtubeviewcount"

val diagnosticBundle = providers.gradleProperty("diagnosticBundle").orNull == "true"

kotlin.sourceSets.named("main") {
    kotlin.exclude(if (diagnosticBundle) "**/ViewCountM1ProbePatch.kt" else "**/RelatedItemsProbePatch.kt")
}

patches {
    about {
        name = "View Count Filter"
        description = if (diagnosticBundle) "Observation-only related-items diagnostic probe for YouTube 21.30.216" else "Independent view-count feed filter for YouTube 21.30.216"
        source = "https://github.com/rikunode/youtube-view-count-filter-patches"
        author = "rikunode"
        contact = "https://github.com/rikunode"
        website = "https://github.com/rikunode/youtube-view-count-filter-patches"
        license = "GPL-3.0-or-later"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn("buildAndroid")
        systemProperty("patchesVersion", project.version.toString())
        systemProperty("patchListOutput", if (diagnosticBundle) "../diagnostic/patches-list.json" else "../patches-list.json")

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
