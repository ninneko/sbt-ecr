lazy val root = project in file(".")
sbtPlugin     := true
name          := "sbt-ecr"
organization  := "com.mintbeans"

sbtVersion in Global := "1.0.3"
crossSbtVersions     := List("0.13.16", "1.1.4")
scalaVersion         := "2.12.4"
scalacOptions        := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

scalaCompilerBridgeSource := {
  val sv = appConfiguration.value.provider.id.version
  ("org.scala-sbt" % "compiler-interface" % sv % "component").sources
}

libraryDependencies ++= {
  val amazonSdkV = "1.11.313"
  val scalaTestV = "3.0.0"
  Seq(
    "com.amazonaws"  %  "aws-java-sdk-sts"   % amazonSdkV,
    "com.amazonaws"  %  "aws-java-sdk-ecr"   % amazonSdkV,
    Defaults.sbtPluginExtra(
      "com.typesafe.sbt" % "sbt-native-packager" % "1.3.4" % "provided",
      (sbtBinaryVersion in pluginCrossBuild).value,
      (scalaBinaryVersion in pluginCrossBuild).value
    ),
    "org.scalatest"  %% "scalatest"      % scalaTestV % "test"
  )
}

