import io.circe.Json
import io.circe.syntax._

object GitHubActionsGenerator {
  object Step {
    val SetupJava: Json = Json.obj(
      "name" := "Setup Java JDK",
      "uses" := "actions/setup-java@v3",
      "with" := Json.obj(
        "cache" := "sbt",
        "distribution" := "temurin",
        "java-version" := "17"
      )
    )

    val Checkout: Json = Json.obj(
      "name" := "Checkout",
      "uses" := "actions/checkout@v2"
    )
  }

  object Job {
    val Lint: Json = Json.obj(
      "name" := "Fatal warnings and code formatting",
      "runs-on" := "ubuntu-latest",
      "steps" := List(
        Step.Checkout,
        Step.SetupJava,
        Json.obj(
          "name" := "Workflows",
          "run" := "sbt -Dmode=ci blowoutCheck"
        ),
        Json.obj(
          "name" := "Code formatting",
          "run" := "sbt -Dmode=ci scalafmtCheckAll"
        ),
        Json.obj(
          "name" := "Fatal warnings",
          "run" := "sbt -Dmode=ci +compile"
        )
      )
    )
  }

  val main: Json = Json.obj(
    "name" := "CI & CD",
    "on" := Json.obj(
      "push" := Json.obj(
        "branches" := List("main"),
        "tags" := List("*.*.*")
      )
    ),
    "jobs" := Json.obj(
      "lint" := Job.Lint,
      "deploy" := Json.obj(
        "name" := "Deploy",
        "runs-on" := "ubuntu-latest",
        "needs" := List("lint"),
        "steps" := List(
          Step.Checkout,
          Step.SetupJava,
          Json.obj(
            "name" := "Release",
            "run" := "sbt -Dmode=release ci-release",
            "env" := Json.obj(
              "PGP_PASSPHRASE" := "${{secrets.PGP_PASSPHRASE}}",
              "PGP_SECRET" := "${{secrets.PGP_SECRET}}",
              "SONATYPE_PASSWORD" := "${{secrets.SONATYPE_PASSWORD}}",
              "SONATYPE_USERNAME" := "${{secrets.SONATYPE_USERNAME}}"
            )
          )
        )
      )
    )
  )

  val branches: Json = Json.obj(
    "name" := "CI",
    "on" := Json.obj(
      "pull_request" := Json.obj(
        "branches" := List("main")
      )
    ),
    "jobs" := Json.obj(
      "lint" := Job.Lint
    )
  )
}
