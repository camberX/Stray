import groovy.json.JsonOutput

// Applied to every version node (versions/<mc>). Shared sources come from the root
// src/ via Stonecutter; versions/<mc>/resources holds the files that differ wholesale.
plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

val mc = stonecutter.current.version
val mcDep = property("mc_dep").toString()
val strayChannel = (findProperty("strayChannel") ?: "release").toString().trim()
val straySha = (findProperty("straySha") ?: "").toString().trim()

version = property("version").toString()
group = property("group").toString()

base {
	archivesName = when (strayChannel) {
		"dev" -> "stray-dev"
		"local" -> "stray-local"
		else -> "stray"
	} + "-mc$mc"
}

repositories {
	mavenCentral()
	exclusiveContent {
		forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
		filter { includeGroup("maven.modrinth") }
	}
}

loom {
	splitEnvironmentSourceSets()
	accessWidenerPath = rootProject.file("src/main/resources/stray.accesswidener")

	mods {
		register("stray") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

sourceSets.named("client") {
	resources.srcDir(project.file("resources/client"))
}

dependencies {
	minecraft("com.mojang:minecraft:${property("minecraft_version")}")
	implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
	// Compile against Sodium so world tint can hook the terrain shader.
	// Not bundled; the game loads Sodium from the user's mods folder.
	"clientCompileOnly"("maven.modrinth:sodium:${property("sodium_version")}")
	"clientCompileOnly"("org.lwjgl:lwjgl-tinyfd:3.4.1")
	"clientImplementation"("net.java.dev.jna:jna:5.17.0")
	"clientImplementation"("net.java.dev.jna:jna-platform:5.17.0")
	include("net.java.dev.jna:jna:5.17.0")
	include("net.java.dev.jna:jna-platform:5.17.0")
	"clientImplementation"("net.raphimc:MinecraftAuth:5.0.0")
	include("net.raphimc:MinecraftAuth:5.0.0")
	include("net.lenni0451.commons:httpclient:1.9.0")
	include("net.lenni0451.commons:gson:1.9.0")
}

tasks.processResources {
	val props = mapOf(
		"version" to project.version.toString(),
		"channel" to strayChannel,
		"sha" to straySha,
		"minecraft" to mcDep
	)
	inputs.properties(props)
	filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
	options.encoding = "UTF-8"
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)
	from(rootProject.file("LICENSE")) {
		rename { "${it}_$projectName" }
	}
}

// Local builds: copy the jar and a latest.json into web/public/mod/<mc>/ (gitignored),
// the same layout CI uploads to R2.
val webModDir = rootProject.layout.projectDirectory.dir("web/public/mod/$mc")
tasks.register("syncWebMod") {
	group = "build"
	description = "Copy the built jar to the shop download path and write latest.json."
	dependsOn("assemble")
	val jarTask = tasks.named<Jar>("jar")
	val versionName = project.version.toString()
	inputs.property("version", versionName)
	doLast {
		val jarFile = jarTask.get().archiveFile.get().asFile
		if (!jarFile.isFile) {
			throw GradleException("Missing $jarFile")
		}
		val destDir = webModDir.asFile
		destDir.mkdirs()
		val versionedName = "stray-$versionName.jar"
		destDir.listFiles()?.filter { it.name.endsWith(".jar") && it.name != versionedName }?.forEach { it.delete() }
		jarFile.copyTo(destDir.resolve(versionedName), overwrite = true)
		val meta = mapOf("version" to versionName, "minecraft" to mc, "file" to versionedName)
		destDir.resolve("latest.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(meta)) + "\n")
	}
}

if (strayChannel != "dev" && strayChannel != "local") {
	tasks.named("build") { finalizedBy("syncWebMod") }
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}

// Packs the client compile classpath jars so the CS2 code can be compile-checked elsewhere.
// Writes ~/cs2/classpath-<mc>.zip.
tasks.register<Zip>("cs2DevClasspath") {
	from(sourceSets["client"].compileClasspath.filter { it.name.endsWith(".jar") })
	archiveFileName = "classpath-$mc.zip"
	destinationDirectory = file("${System.getProperty("user.home")}/cs2")
}

// Rebuilds the knife data pack from a local CS2 install. Release builds download the pack from
// the cs2-data branch instead; a pack left here (gitignored) is bundled into local builds.
tasks.register<JavaExec>("cs2Pack") {
	dependsOn("compileJava", "compileClientJava")
	classpath = files(sourceSets["main"].output.classesDirs, sourceSets["client"].output.classesDirs) + sourceSets["client"].runtimeClasspath
	mainClass = "dev.stray.client.cs2.pack.Cs2PackBuilder"
	val pack = rootProject.file("src/client/resources/assets/stray/cs2/cs2.pack")
	args(pack.absolutePath)
	maxHeapSize = "4g"
	doFirst { pack.parentFile.mkdirs() }
}

tasks.named("processClientResources") {
	mustRunAfter("cs2Pack")
}
