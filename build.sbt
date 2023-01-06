val Version = new {
  val Fs2 = "3.7.0"
  val Http4s = "1.0.0-M40"
  val Log4Cats = "2.6.0"
  val Scala = "3.3.0"
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
  .aggregate(core)

lazy val core = module(identifier = Some("core")).settings(
  libraryDependencies ++=
    "co.fs2" %% "fs2-io" % Version.Fs2 ::
      "org.http4s" %% "http4s-ember-client" % Version.Http4s ::
      "org.typelevel" %% "log4cats-noop" % Version.Log4Cats ::
      Nil
)
