import play.PlayScala

name := "txbits"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  jdbc,
  anorm,
  filters,
  play.PlayImport.cache,
  "com.typesafe.play.plugins" %% "play-plugins-util" % "2.3.0",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.mockito" % "mockito-all" % "1.9.5",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "com.github.briandilley.jsonrpc4j" % "jsonrpc4j" % "1.1",
  "org.postgresql" % "postgresql" % "9.3-1103-jdbc4",
  "com.google" % "bitcoinj" % "0.11",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "com.github.mumoshu" %% "play2-memcached" % "0.6.0",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
  "org.bouncycastle" % "bcpg-jdk15on" % "1.51",
  "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.51",
  "org.bouncycastle" % "bcmail-jdk15on" % "1.51"
)

resolvers ++= Seq(
  "Spy Repository" at "http://files.couchbase.com/maven2",
  "bitcoinj" at "http://distribution.bitcoinj.googlecode.com/git/releases",
  Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalacOptions ++= Seq("-deprecation", "-feature")

scalariformSettings
