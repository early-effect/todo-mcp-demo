import sbt.librarymanagement.syntax.*
import zipx.*

object DemoVersions extends ZipxVersions:
  val sbt: SbtVersion     = SbtVersion("2.1.0-M3")
  val scala: ScalaVersion = ScalaVersion("3.9.0")

  val zio        = Lib("dev.zio", "zio", "2.1.26")
  val zioTest    = zio.mod("zio-test").test
  val zioTestSbt = zio.mod("zio-test-sbt").test
  val zioJson    = Lib("dev.zio", "zio-json", "1.1.0")

  val heddle     = Lib("rocks.earlyeffect", "heddle", "0.9.0-SNAPSHOT")
  val heddleMcp  = heddle.mod("heddle-mcp")
  val heddleApps = heddle.mod("heddle-mcp-apps")

  val ascentJs      = Lib("rocks.earlyeffect", "ascent-js", "0.10.1-SNAPSHOT")
  val ascentCss     = Lib("rocks.earlyeffect", "ascent-css", "0.10.0-SNAPSHOT")
  val ascentHistory = Lib("rocks.earlyeffect", "ascent-history", "0.10.0-SNAPSHOT")

  val scalajs  = Plugin("org.scala-js", "sbt-scalajs", "1.22.0")
  val scalafmt = Plugin("org.scalameta", "sbt-scalafmt", "2.6.2")

  val ascentMcpApp = Lib("rocks.earlyeffect", "ascent-mcp-app", "0.10.0-SNAPSHOT")
  val heddleHost   = heddle.mod("heddle-mcp-apps-host")
  val heddleFrame  = heddle.mod("heddle-mcp-apps-frame")

  def sharedLib = library(zio, zioJson, heddle, heddleMcp, heddleApps)
  def serverLib = library(zio, zioJson, heddle, heddleMcp, heddleApps)
  def pageLib   = library(zio, zioJson, heddle, ascentJs, ascentCss, ascentHistory)
  def viewLib   = library(zio, zioJson, heddle, heddleApps, ascentJs, ascentCss, ascentHistory, ascentMcpApp)
  def hostLib   = library(zio, heddle, heddleMcp, heddleApps, heddleHost, heddleFrame, ascentJs)
  def testLib   = library(zioTest, zioTestSbt)
