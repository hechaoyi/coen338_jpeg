Compile:

mvn package
(At least JDK8 is required. Maven building tool is required.)



Run:

java -jar target/jpeg-recompress-1.0-SNAPSHOT.jar -analysis /path/to/xxx.jpg
(to show the DC/AC statistics information in JPEG file)

java -jar target/jpeg-recompress-1.0-SNAPSHOT.jar -encode /path/to/xxx.jpg
(to generate a .jpp file in current folder, which is the compression result)

java -jar target/jpeg-recompress-1.0-SNAPSHOT.jar -decode ./xxx.jpp
(to generate a .jpg file in current folder, which is the decompression result and supposed to be matched with the original JPEG file bitwise identically)
