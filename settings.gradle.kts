pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.7"
}

// One source tree for every Minecraft version. Version-specific code sits in
// `//? if` blocks (see stonecutter.gradle.kts for the automatic renames), and the
// files that differ wholesale (shaders, mixin lists) live in versions/<mc>/resources.
stonecutter {
	create(rootProject) {
		versions("26.2", "26.3")
		vcsVersion = "26.3"
	}
}

rootProject.name = "stray"
