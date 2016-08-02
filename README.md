# HTRC-EF-RsyncGenerator
This tool generates a shell script that allows one to download the extracted features files for the volumes of a given workset.

# Compile
`> sbt clean assembly`

# Run
`> java -jar target/scala-2.11/htrc-ef-rsyncgenerator-<VERSION>.jar`

*Note*: This expects that a file called `collection.properties` exists in the current folder, containing configuration information.

*Example configuration file*:
```
collectionLocation = workset_ids.txt
outputDir = /tmp/files
outputFile = EF_Rsync.sh
```

With this configuration file, the tool generates a script file called `EF_Rsync.sh` that can be found in the folder `/tmp/files/`