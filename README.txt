The apache transport implementation found in org.fourthline.cling.transport.impl.apache
uses repackaged httpclient/httpcore 4.1.1 (otherwise it conflicts with Android Apache classes which 
are from an earlier and old version)

To install the repackaged httclient/httpcore jars found in lib/ into your local repository, issue the following maven commands: 

mvn install:install-file -DgroupId=com.bubblesoft.org.apache.httpcomponents -DartifactId=httpclient-repackaged -Dversion=4.1.1 -Dpackaging=jar -Dfile=lib/httpclient-repackaged-4.1.1.jar
mvn install:install-file -DgroupId=com.bubblesoft.org.apache.httpcomponents -DartifactId=httpcore-repackaged -Dversion=4.1.1 -Dpackaging=jar -Dfile=lib/httpcore-repackaged-4.1.1.jar