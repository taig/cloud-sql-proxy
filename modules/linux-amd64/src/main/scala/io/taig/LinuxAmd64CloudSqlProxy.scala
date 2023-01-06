package io.taig

import cats.effect.{Async, Resource}

object LinuxAmd64CloudSqlProxy:
  def apply[F[_]: Async](arguments: List[String]): Resource[F, Unit] =
    CloudSqlProxy.fromClasspath("cloud-sql-proxy.linux.amd64")(arguments)
