
name := "aws4-request-signer"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.asynchttpclient" % "async-http-client" % "2.0.11",
  "com.amazonaws" % "aws-java-sdk" % "1.11.98",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.netaporter" %% "scala-uri" % "0.4.16" % "test"
)