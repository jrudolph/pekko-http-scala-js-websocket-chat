val scalaV = "3.3.4"
val pekkoV = "1.1.1"
val pekkoHttpV = "1.1.0"

val upickleV = "4.0.2"
val utestV = "0.8.4"
val scalaJsDomV = "2.8.0"
val specs2V = "5.5.3"

lazy val root =
  project.in(file("."))
    .aggregate(frontend, backend, cli)

// Scala-Js frontend
lazy val frontend =
  project.in(file("frontend"))
    .enablePlugins(ScalaJSPlugin)
    .settings(commonSettings: _*)
    .settings(
      scalaJSUseMainModuleInitializer := true,
      testFrameworks += new TestFramework("utest.runner.Framework"),
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJsDomV,
        "com.lihaoyi" %%% "utest" % utestV % "test"
      )
    )
    .dependsOn(sharedJs)

// Akka Http based backend
lazy val backend =
  project.in(file("backend"))
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.apache.pekko" %% "pekko-stream" % pekkoV,
        "org.apache.pekko" %% "pekko-http" % pekkoHttpV,
        "org.specs2" %% "specs2-core" % specs2V % "test",
        "com.lihaoyi" %% "upickle" % upickleV
      ),
      Compile / resourceGenerators += Def.task {
        val f1 = (frontend / Compile / fastOptJS).value.data
        Seq(f1, new File(f1.getPath+".map"))
      }.taskValue,
      watchSources ++= (frontend / watchSources).value
    )
    .dependsOn(sharedJvm)

lazy val cli =
  project.in(file("cli"))
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.apache.pekko" %% "pekko-stream" % pekkoV,
        "org.apache.pekko" %% "pekko-http-core" % pekkoHttpV,
        "org.specs2" %% "specs2-core" % specs2V % "test",
        "com.lihaoyi" %% "upickle" % upickleV
      ),
      run / fork := true,
      run / connectInput := true,
      assemblyJarName := "../cli.jar"
    )
    .dependsOn(sharedJvm)

lazy val shared =
  (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in file ("shared"))
    .settings(
      scalaVersion := scalaV,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % upickleV,
    )


lazy val sharedJvm= shared.jvm
lazy val sharedJs= shared.js

def commonSettings = Seq(
  scalaVersion := scalaV,
  scalacOptions ++= Seq("-deprecation", "-feature", "-encoding", "utf8", "-unchecked", "-Xlint"),
  resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/"),
  resolvers += "Apache Nexus Staging".at("https://repository.apache.org/content/repositories/staging/"),
)
