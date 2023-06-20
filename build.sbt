name := """trading-bot-public"""
organization := "com.impasse"

version := "2.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  guice, ws,
  "com.github.tototoshi" %% "scala-csv" % "1.3.8",
  "com.typesafe.akka" %% "akka-stream" % "2.6.14",
  "com.zerodhatech.kiteconnect" % "kiteconnect" % "3.1.14",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "joda-time" % "joda-time" % "2.10.10",
  "com.typesafe.akka" %% "akka-actor" % "2.5.32",
  "com.typesafe.slick" %% "slick" % "3.3.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42" //org.postgresql.ds.PGSimpleDataSource dependency
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.impasse.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.impasse.binders._"

scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-deprecation",
  "-unchecked"
)
