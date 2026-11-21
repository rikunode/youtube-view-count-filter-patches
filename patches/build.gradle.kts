group = "app.rikunode.youtubeviewcount"

patches {
    about {
        name = "Rikunode View Count Filter"
        description = "Independent view-count feed filter for YouTube 21.30.216"
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

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
