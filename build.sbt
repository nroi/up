name := """up"""
organization := "click.helios"

version := "1.0-SNAPSHOT"

maintainer := "Fabian Muscariello <fabian.muscariello@mailbox.org>"

packageSummary := "fileupload web app"

packageDescription := """Web app to upload files after having obtained a token."""

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

libraryDependencies += filters
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "click.helios.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "click.helios.binders._"

enablePlugins(SbtNativePackager)

import com.typesafe.sbt.packager.archetypes.ServerLoader

serverLoading in Debian := ServerLoader.Systemd
