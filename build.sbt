libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "2.2.2" % "test"
)

scalaVersion := "2.10.3"

ScalariformSupport.formatSettings

Revolver.settings
