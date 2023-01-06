package io.taig

import cats.effect.*
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.file.Files
import fs2.io.readClassLoaderResource

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
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

  def fromClasspath[F[_]: Async](script: String)(arguments: List[String]): Resource[F, Unit] =
    fromStream(readClassLoaderResource(script))(arguments)

  def fromStream[F[_]](script: Stream[F, Byte])(arguments: List[String])(using F: Async[F]): Resource[F, Unit] =
    Files[F].tempFile
      .evalTap(path => script.through(Files[F].writeAll(path)).compile.drain)
      .map(_.toNioPath)
      .evalTap(path => F.blocking(path.toFile.setExecutable(true)))
      .flatMap(fromPath[F](_)(arguments))

  def fromPath[F[_]](script: Path)(arguments: List[String])(using F: Async[F]): Resource[F, Unit] = (
    Dispatcher.sequential(await = true),
    Resource.eval(Deferred[F, Unit]),
    Resource.eval(F.delay(collection.mutable.ListBuffer.empty[String]))
  ).flatMapN { (dispatcher, signal, messages) =>
    Resource
      .make {
        F.blocking {
          val logger = new ProcessLogger:
            override def out(s: => String): Unit =
              messages.addOne(s)
              if (s.contains("The proxy has started successfully and is ready for new connections!"))
                dispatcher.unsafeRunAndForget(signal.complete(()))
            override def err(s: => String): Unit = messages.addOne(s)
            override def buffer[T](f: => T): T = f

          Process(List(script.toAbsolutePath.toString) ++ arguments).run(logger)
        }
      }(process => F.blocking(process.destroy()))
      .evalMap { process =>
        val exit = F.interruptible(process.exitValue()).flatMap { code =>
          val message = s"""Failed to start Google Cloud Proxy (exit code $code):
                           |${messages.mkString("\n")}""".stripMargin
          F.raiseError(new IllegalStateException(message))
        }

        F.race(signal.get, exit).void
      }
  }
