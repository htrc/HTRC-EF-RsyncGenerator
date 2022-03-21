showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/repository/maven-public"
  ),
  externalResolvers := Resolver.combineDefaultResolvers(resolvers.value.toVector, mavenCentral = false),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  ),
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.diff(Seq(
    Wart.DefaultArguments,
    Wart.NonUnitStatements,
    Wart.Any,
    Wart.StringPlusAny
  ))
)

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _ ⇒  "2.5.2"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  connectInput := true,
  outputStrategy := Some(StdoutOutput)
)

lazy val `rsync-generator` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging)
  .settings(commonSettings)
  .settings(ammoniteSettings)
  .settings(
    name := "rsync-generator",
    description :=
      "Generates a shell script that allows one to download the extracted features " +
      "files for the volumes of a given workset.",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.hathitrust.htrc"           %% "data-model"           % "2.13",
      "org.hathitrust.htrc"           %% "scala-utils"          % "2.13",
      "org.rogach"                    %% "scallop"              % "4.1.0",
      "org.scalatest"                 %% "scalatest"            % "3.2.11"      % Test
    ),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
  )
