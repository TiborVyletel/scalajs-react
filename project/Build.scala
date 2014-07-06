import sbt._
import Keys._

import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

object ScalajsReact extends Build {

  val Scala211 = "2.11.1"

  val root = Project("scalajs-react", file("."))
    .settings(scalaJSSettings: _*)
    .settings(
      name                 := "scalajs-react",
      homepage             := Some(url("https://github.com/japgolly/scalajs-react")),
      version              := "0.1.0",
      scalaVersion         := Scala211,
      crossScalaVersions   := Seq("2.10.4", Scala211),
      scalacOptions       ++= Seq("-deprecation", "-unchecked", "-language:_"),
      libraryDependencies ++= Seq(
                                "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6",
                                "com.scalatags" %%% "scalatags" % "0.3.5")
    )

}
