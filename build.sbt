libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "2.3.12" % "test"
)

scalaVersion := "2.11.2"

ScalariformSupport.formatSettings

Revolver.settings
