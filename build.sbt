val akkaHttpV = "10.2.0-RC2"
val scalaV = "2.13.3"
val akkaV = "2.6.7"
val upickleV = "1.2.0"
val utestV = "0.7.4"
val scalaJsDomV = "1.0.0"
val specs2V = "4.8.0"

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
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "org.specs2" %% "specs2-core" % specs2V % "test",
        "com.lihaoyi" %% "upickle" % upickleV
      ),
      resourceGenerators in Compile += Def.task {
        val f1 = (fastOptJS in Compile in frontend).value.data
        Seq(f1, new File(f1.getPath+".map"))
      }.taskValue,
      watchSources ++= (watchSources in frontend).value
    )
    .dependsOn(sharedJvm)

lazy val cli =
  project.in(file("cli"))
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-core" % akkaHttpV,
        "org.specs2" %% "specs2-core" % specs2V % "test",
        "com.lihaoyi" %% "upickle" % upickleV
      ),
      fork in run := true,
      connectInput in run := true
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
  resolvers += "staging" at "https://dl.bintray.com/akka/maven"
)