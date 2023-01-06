val Version = new {
  val Fs2 = "3.4.0"
  val Scala = "3.2.1"
}

def module(identifier: Option[String]): Project = {
  Project(identifier.getOrElse("root"), file(identifier.fold(".")("modules/" + _)))
    .settings(
      Compile / scalacOptions += "-source:future",
      name := "cloud-sql-proxy" + identifier.fold("")("-" + _)
    )
}

inThisBuild(
  Def.settings(
    developers := List(Developer("taig", "Niklas Klein", "mail@taig.io", url("https://taig.io/"))),
    dynverVTagPrefix := false,
    homepage := Some(url("https://github.com/taig/cloud-sql-proxy/")),
    licenses := List("MIT" -> url("https://raw.githubusercontent.com/taig/cloud-sql-proxy/main/LICENSE")),
    organization := "io.taig",
    organizationHomepage := Some(url("https://taig.io/")),
    scalaVersion := Version.Scala,
    versionScheme := Some("early-semver")
  )
)

lazy val root = module(identifier = None)
  .enablePlugins(BlowoutYamlPlugin)
  .settings(noPublishSettings)
  .settings(
    blowoutGenerators ++= {
      val workflows = file(".github") / "workflows"
      BlowoutYamlGenerator.lzy(workflows / "main.yml", GitHubActionsGenerator.main) ::
        BlowoutYamlGenerator.lzy(workflows / "branches.yml", GitHubActionsGenerator.branches) ::
        Nil
    }
  )
  .aggregate(core, darwinAmd64, linuxAmd64)

lazy val core = module(identifier = Some("core")).settings(
  libraryDependencies ++=
    "co.fs2" %% "fs2-io" % Version.Fs2 ::
      Nil
)

lazy val darwinAmd64 = module(identifier = Some("darwin-amd64")).dependsOn(core)
lazy val linuxAmd64 = module(identifier = Some("linux-amd64")).dependsOn(core)
