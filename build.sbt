ThisBuild / version        := "0.0.1"
ThisBuild / scalaVersion   := "2.13.18"
ThisBuild / scalafmtConfig := file(".scala-config/.scalafmt.conf")

// Catch dead code automatically: stale code rots quietly otherwise, as the
// `MethodSourceCoverage.Empty` orphan that survived the fail-fast refactor showed.
// `-Wunused:imports/privates/locals` flag references the compiler can prove unused;
// `-Wdead-code` flags unreachable statements. Warnings, not errors, so a transient
// scratch-file unused import during development doesn't block compile.
ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wdead-code"
)

lazy val sut = (project in file("sut"))
  .settings(
    coverageEnabled := true
  )

lazy val engine = (project in file("engine"))
  .dependsOn(sut)
  .settings(
    fork                          := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / run / mainClass     := Some("app.Main"),
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect"                 % "3.7.0",
      "org.scalacheck" %% "scalacheck"                  % "1.19.0",
      "org.scalameta"  %% "scalameta"                   % "4.17.0",
      "org.scoverage"  %% "scalac-scoverage-serializer" % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-reporter"   % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-domain"     % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-runtime"    % "2.5.2"
    )
  )

lazy val root = (project in file("."))
  .aggregate(sut, engine)
  .settings(name := "scala-coverage-based-pbt")
