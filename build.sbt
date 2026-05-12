ThisBuild / version        := "0.0.1"
ThisBuild / scalaVersion   := "2.13.18"
ThisBuild / scalafmtConfig := file(".scala-config/.scalafmt.conf")

lazy val sut = (project in file("sut"))
  .settings(
    coverageEnabled := true
  )

lazy val engine = (project in file("engine"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect"                 % "3.7.0",
      "org.scalacheck" %% "scalacheck"                  % "1.19.0",
      "org.scalameta"  %% "scalameta"                   % "4.17.0",
      "org.scoverage"  %% "scalac-scoverage-serializer" % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-reporter"   % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-domain"     % "2.5.2",
      "org.scoverage"  %% "scalac-scoverage-runtime"    % "2.5.2",
      "org.jacoco"     %  "org.jacoco.core"             % "0.8.14",
      "org.jacoco"     %  "org.jacoco.agent"            % "0.8.14",
      "org.jacoco"     %  "org.jacoco.agent"            % "0.8.14" classifier "runtime"
    )
  )

lazy val runner = (project in file("runner"))
  .dependsOn(engine, sut)
  .settings(
    fork                          := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,
    javaOptions ++= {
      val agentJar = (engine / update).value.allFiles.find { f =>
        f.getName.startsWith("org.jacoco.agent-") && f.getName.endsWith("-runtime.jar")
      }.getOrElse(sys.error("JaCoCo agent runtime jar not found in engine's update report"))
      Seq(s"-javaagent:${agentJar.getAbsolutePath}=includes=benchmark.*")
    }
  )

lazy val root = (project in file("."))
  .aggregate(sut, engine, runner)
  .settings(name := "scala-coverage-based-pbt")
