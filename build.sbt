showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "edu.illinois.i3.htrc.tools",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.11.8",
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

lazy val `htrc-ef-rsyncgenerator` = (project in file(".")).
  enablePlugins(GitVersioning, GitBranchPrompt).
  settings(commonSettings: _*).
  settings(
    name := "htrc-ef-rsyncgenerator",
    version := "1.1",
    description :=
      "Generates a shell script that allows one to download the extracted features " +
      "files for the volumes of a given workset.",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "edu.illinois.i3.htrc.tools"    %  "htrc-pairtree-helper" % "2.0",
      "com.jsuereth"                  %% "scala-arm"            % "1.4",
      "org.scalatest"                 %% "scalatest"            % "2.2.6"      % Test
    ),
    assemblyJarName in assembly := s"${name.value}-${version.value}.jar"
  )
