showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:implicitConversions", "-target:jvm-1.7"),
  javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
  resolvers ++= Seq(
    "I3 Repository" at "http://nexus.htrc.illinois.edu/content/groups/public",
    Resolver.mavenLocal
  ),
  packageOptions in (Compile, packageBin) += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  )
)

lazy val `rsync-generator` = (project in file(".")).
  enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging).
  settings(commonSettings: _*).
  settings(
    name := "rsync-generator",
    version := "2.1-SNAPSHOT",
    description :=
      "Generates a shell script that allows one to download the extracted features " +
      "files for the volumes of a given workset.",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.hathitrust.htrc"           %  "pairtree-helper"      % "3.0"
        exclude("com.beust", "jcommander"),
      "org.rogach"                    %% "scallop"              % "2.0.0",
      "com.jsuereth"                  %% "scala-arm"            % "1.4",
      "org.scalatest"                 %% "scalatest"            % "2.2.6"      % Test
    ),
    assemblyJarName in assembly := s"${name.value}-${version.value}.jar"
  )
