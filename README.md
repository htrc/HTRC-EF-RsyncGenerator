# HTRC-EF-RsyncGenerator
This tool generates a shell script that allows one to download the extracted features 
files for a given set of volume IDs.

# Build
* To generate a "fat" runnable JAR  
  `sbt clean assembly`  
  and look for it in `target/scala-2.11/` folder.

  *Note:* you can run the JAR via the usual: `java -jar JARFILE`
  
* To generate a package that can be invoked via a shell script  
  `sbt stage`  
  and look for it in `target/universal/stage/` folder.
  

# Run
The rsync generator tool can be configured to run in two ways:
* Using file `collection.properties` as input  
  
  `java -jar rsync-generator-<VERSION>.jar`  
  
  By not specifying a command line argument, the tool expects that a file called 
  `collection.properties` exists in the current folder, containing configuration information.

  *Example configuration file*:
  ```
  collectionLocation = workset_ids.txt
  outputDir = /tmp/files
  outputFile = EF_Rsync.sh
  ```

  With this configuration file, the tool generates a script file called `EF_Rsync.sh` that 
  can be found in the folder `/tmp/files/`
  
* Using command line arguments
  
  ```
  rsync-generator
  HathiTrust Research Center
    -o, --output  <FILE>   Writes the generated rsync script to FILE
        --help             Show help message
        --version          Show version of this program
  
   trailing arguments:
    ids (not required)   The file containing the list of HT IDs to rsync (if
                         omitted, will read from stdin)
  ```
