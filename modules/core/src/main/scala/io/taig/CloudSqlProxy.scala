package io.taig

import cats.effect.*
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import fs2.io.file.{Files, Path, PosixPermission, PosixPermissions}
import fs2.io.net.Network
import org.http4s.client.UnexpectedStatus
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.uri
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.LoggerFactory

import scala.sys.process.*

object CloudSqlProxy:
  object Arguments:
    def apply(
        instanceConnectionName: String,
        port: Option[Int] = none,
        credentialsFile: Option[Path] = none,
        gcloudAuth: Boolean = false,
        token: Option[String] = none
    ): List[String] = port.fold(Nil)(port => "--port" :: s"$port" :: Nil) ++
      credentialsFile.fold(Nil)(path => "--credentials-file" :: path.toString :: Nil) ++
      Option.when(gcloudAuth)("--gcloud-auth").toList ++
      token.fold(Nil)(token => "--token" :: token :: Nil) ++
      List(instanceConnectionName)

  enum Variant:
    case DarwinAmd64
    case DarwinArm64
    case Linux386
    case LinuxAmd64
    case LinuxArm
    case LinuxArm64
    case X64
    case X86

    override def toString: String = this match
      case DarwinAmd64 => "darwin.amd64"
      case DarwinArm64 => "darwin.arm64"
      case Linux386    => "linux.386"
      case LinuxAmd64  => "linux.amd64"
      case LinuxArm    => "linux.arm"
      case LinuxArm64  => "linux.arm64"
      case X64         => "x64"
      case X86         => "x86"

  def downloadScript[F[_]: Async: Files: Network: LoggerFactory](
      target: Path,
      version: String,
      variant: CloudSqlProxy.Variant
  ): F[Unit] = EmberClientBuilder
    .default[F]
    .build
    .use: client =>
      val request = Request[F](
        method = Method.GET,
        uri = uri"https://storage.googleapis.com" /
          "cloud-sql-connectors" /
          "cloud-sql-proxy" /
          s"v$version" /
          s"cloud-sql-proxy.$variant"
      )

      client
        .run(request)
        .use: response =>
          if (response.status === Status.Ok) response.body.through(Files[F].writeAll(target)).compile.drain
          else UnexpectedStatus(response.status, request.method, request.uri).raiseError[F, Unit]

  def downloadTempScript[F[_]: Async: Files: Network: LoggerFactory](
      version: String,
      variant: CloudSqlProxy.Variant
  ): Resource[F, Path] = Files[F].tempFile.evalTap: target =>
    downloadScript[F](target, version, variant) *>
      Files[F].setPosixPermissions(target, PosixPermissions(PosixPermission.OwnerExecute))

  def fromPath[F[_]](script: Path)(arguments: List[String])(using F: Async[F]): Resource[F, Unit] = (
    Dispatcher.sequential(await = true),
    Resource.eval(Deferred[F, Unit]),
    Resource.eval(F.delay(collection.mutable.ListBuffer.empty[String]))
  ).flatMapN: (dispatcher, signal, messages) =>
    Resource
      .make {
        F.blocking:
          val logger = new ProcessLogger:
            override def out(s: => String): Unit =
              messages.addOne(s)
              if (s.contains("The proxy has started successfully and is ready for new connections!"))
                dispatcher.unsafeRunAndForget(signal.complete(()))
            override def err(s: => String): Unit = messages.addOne(s)
            override def buffer[T](f: => T): T = f

          Process(List(script.absolute.toString) ++ arguments).run(logger)
      }(process => F.blocking(process.destroy()))
      .evalMap: process =>
        val exit = F
          .interruptible(process.exitValue())
          .flatMap: code =>
            val message = s"""Failed to start Google Cloud Proxy (exit code $code):
                             |${messages.mkString("\n")}""".stripMargin
            F.raiseError(new IllegalStateException(message))

        F.race(signal.get, exit).void
