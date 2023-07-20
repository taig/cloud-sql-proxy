# cloud-sql-proxy

![Maven Central](https://img.shields.io/maven-central/v/io.taig/cloud-sql-proxy-core_3)

> A Scala wrapper around the cloud-sql-proxy executable to connect to Google Cloud SQL instances

# DEPRECATED

See https://github.com/typelevel/skunk/issues/928 as a preferred solution

## Motivation

An easy way to connect a Skunk application from Google Cloud Run to Google Cloud SQL.

## Installation

```sbt
libraryDependencies ++=
  "io.taig" % "cloud-sql-proxy-core" % "x.y.z" ::
  Nil
```