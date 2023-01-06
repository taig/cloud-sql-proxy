package io.taig

import cats.effect.{Async, Resource}

object DarwinAmd64CloudSqlProxy:
  def apply[F[_]: Async](arguments: List[String]): Resource[F, Unit] =
    CloudSqlProxy.fromClasspath("cloud-sql-proxy.darwin.amd64")(arguments)
