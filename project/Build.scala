import sbt._
import Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin.autoImport._
import xerial.sbt.Sonatype._
import SonatypeKeys._
import com.typesafe.sbt.pgp.PgpKeys

object AutomagicBuild extends Build {

  val root = Project(id = "automagic", base = file("."))
    .settings(sonatypeSettings: _*)
    .settings(ReleasePlugin.projectSettings: _*)
    .settings(mavenSettings: _*)
    .settings(
      organization := "com.github.cb372",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseCrossBuild := true,
      libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.0.1" % "test"
      )
    )

    lazy val mavenSettings = Seq(
    pomExtra :=
      <url>https://github.com/cb372/automagic</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:cb372/automagic.git</url>
        <connection>scm:git:git@github.com:cb372/automagic.git</connection>
      </scm>
      <developers>
        <developer>
          <id>cb372</id>
          <name>Chris Birchall</name>
          <url>https://github.com/cb372</url>
        </developer>
      </developers>,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }
  )

}
