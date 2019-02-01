fork in run := true
connectInput in run := true
cancelable in Global := true

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ypartial-unification"
)

resolvers += Resolver.sonatypeRepo("releases")

// initialCommands in console := """""".stripMargin

val finatraVersion = "19.1.0"
val copyrightName = "N. Justus"
val copyrightYear = "2018"
val license = "MPLv2"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin).
  .settings(Common.settings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version,
      BuildInfoKey.action("license") (s"(c) $copyrightYear $copyrightName - $license"),
      BuildInfoKey.action("commit") {
        Common.latestCommitHash()
      }), // re-computed each time at compile,
    headerLicense := Some(HeaderLicense.MPLv2(copyrightYear, copyrightName)),
    name := "webmodelica",
    mainClass in Compile := Some("webmodelica.core.WMServerMain"),
    libraryDependencies ++= Dependencies.deps
    // dependencyOverrides ++= Seq(
    //   "com.google.guava"         % "guava"  % "19.0"
    // ),
  )

mainClass in assembly := (mainClass in Compile).value
assemblyJarName in assembly := s"${name.value}-server-${version.value}-${Common.latestCommitHash()}.jar"
