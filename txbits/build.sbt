name := "txbits"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ws,
  jdbc,
  "com.typesafe.play" %% "anorm" % "2.3.9",
  filters,
  play.PlayImport.cache,
  specs2 % Test,
  evolutions,
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.mockito" % "mockito-all" % "1.10.19",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "com.github.briandilley.jsonrpc4j" % "jsonrpc4j" % "1.1",
  "org.postgresql" % "postgresql" % "9.3-1103-jdbc41",
  "org.bitcoinj" % "bitcoinj-core" % "0.12",
  "org.apache.commons" % "commons-email" % "1.3.3",
//XXX: TEMPORARILY DISABLED
  //"com.github.mumoshu" %% "play2-memcached-play24" % "0.7.0",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
  "org.bouncycastle" % "bcpg-jdk15on" % "1.51",
  "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.51",
  "org.bouncycastle" % "bcmail-jdk15on" % "1.51"
)

resolvers ++= Seq(
  "Spy Repository" at "http://files.couchbase.com/maven2",
  Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalacOptions ++= Seq("-deprecation", "-feature")

scalariformSettings
