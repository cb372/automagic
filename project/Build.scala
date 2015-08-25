import sbt._
import Keys._

object AutomagicBuild extends Build {

  val root = Project(id = "automagic", base = file("."))
    .settings(
      organization := "com.github.cb372",
      scalaVersion := "2.11.7",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      libraryDependencies <+= scalaVersion { s =>
        "org.scala-lang" % "scala-reflect" % s
      },
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.2.5" % "test"
      )
    )

}
