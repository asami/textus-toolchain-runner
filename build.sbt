import org.goldenport.cozy.CozyPlugin.autoImport._
import sbt.Keys.*

val scala3Version = "3.3.8"
val cncfVersion = "0.5.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    organization := "org.simplemodeling.textus",
    name := "textus-toolchain-runner",
    version := "0.2.0-SNAPSHOT",

    scalaVersion := scala3Version,
    useCoursier := false,

    resolvers += Resolver.defaultLocal,
    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",

    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % cncfVersion,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % Test,

    cozyGeneratorBackend := "cozy",
    cozyDelegateProjectDir := None,
    cozyDelegateCommand := Seq("cozy"),
    cozyManifestMetadata ++= Map(
      "component" -> "textus-toolchain-runner",
      "boundedContext" -> "default",
      "domain" -> "default"
    ),
    publish := {
      val _ = cozyPublishCar.value
      ()
    },
    publishLocal := {
      val _ = cozyPublishLocalCar.value
      ()
    },

    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "org" / "simplemodeling" / "textus" / "toolchainrunner" / "meta" / "BuildVersion.scala"
      val content =
        "package org.simplemodeling.textus.toolchainrunner.meta\n\nobject BuildVersion {\n" +
          "  val name: String = \"" + name.value + "\"\n" +
          "  val version: String = \"" + version.value + "\"\n" +
          "  val scalaVersion: String = \"" + scalaVersion.value + "\"\n" +
          "}\n"
      IO.createDirectory(out.getParentFile)
      IO.write(out, content)
      Seq(out)
    }.taskValue
  )
