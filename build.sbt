import org.scalajs.linker.interface.ModuleKind

DemoVersions.settings

ThisBuild / organization    := "rocks.earlyeffect"
ThisBuild / zipxJavaVersion := JdkVersion("25")

val scala3 = DemoVersions.scala.toString
val scalas = Seq(scala3)

def skipPublish = Seq(publish / skip := true, publishArtifact := false)

lazy val shared = (projectMatrix in file("shared"))
  .settings(
    name := "todo-shared",
    skipPublish,
    DemoVersions.sharedLib,
    DemoVersions.testLib,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )
  .jvmPlatform(scalaVersions = scalas)
  .jsPlatform(scalaVersions = scalas)

lazy val widgets = (projectMatrix in file("widgets"))
  .dependsOn(shared)
  .settings(
    name := "todo-widgets",
    skipPublish,
    DemoVersions.pageLib,
  )
  .jsPlatform(scalaVersions = scalas)

lazy val view = (projectMatrix in file("view"))
  .dependsOn(shared, widgets)
  .settings(
    name := "todo-view",
    skipPublish,
    DemoVersions.viewLib,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
  )
  .jsPlatform(scalaVersions = scalas)

lazy val host = (projectMatrix in file("host"))
  .settings(
    name := "todo-host",
    skipPublish,
    DemoVersions.hostLib,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
  )
  .jsPlatform(scalaVersions = scalas)

lazy val page = (projectMatrix in file("page"))
  .dependsOn(shared, widgets)
  .settings(
    name := "todo-page",
    skipPublish,
    DemoVersions.pageLib,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
  )
  .jsPlatform(scalaVersions = scalas)

lazy val server = project
  .in(file("server"))
  .dependsOn(shared.jvm(scala3))
  .settings(
    name := "todo-server",
    skipPublish,
    DemoVersions.serverLib,
    DemoVersions.testLib,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Compile / resourceGenerators += Def.task {
      val linkedPage = (page.js(scala3) / Compile / fastLinkJS).value
      val linkedView = (view.js(scala3) / Compile / fastLinkJS).value
      val linkedHost = (host.js(scala3) / Compile / fastLinkJS).value
      val _          = (linkedPage, linkedView, linkedHost)
      val dir        = (Compile / resourceManaged).value
      val pageJs     = dir / "page.js"
      val viewJs     = dir / "todo-view.js"
      val hostJs     = dir / "todo-host.js"
      IO.copyFile((page.js(scala3) / Compile / fastLinkJSOutput).value / "main.js", pageJs)
      IO.copyFile((view.js(scala3) / Compile / fastLinkJSOutput).value / "main.js", viewJs)
      IO.copyFile((host.js(scala3) / Compile / fastLinkJSOutput).value / "main.js", hostJs)
      Seq(pageJs, viewJs, hostJs)
    }.taskValue,
  )

lazy val root = project
  .in(file("."))
  .aggregate(
    (shared.projectRefs ++ widgets.projectRefs ++ page.projectRefs ++ view.projectRefs ++ host.projectRefs ++
      Seq[sbt.ProjectReference](server))*
  )
  .settings(skipPublish, name := "todo-mcp-demo")
