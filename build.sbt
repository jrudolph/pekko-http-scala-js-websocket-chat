libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.13" % "test"
)

scalaVersion := "2.10.2"

ScalariformSupport.formatSettings

Revolver.settings
