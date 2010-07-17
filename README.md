Neo4j Osmosis Plugin
=============
 
[Osmosis](http://wiki.openstreetmap.org/wiki/Osmosis) is a command line java app for processing OpenStreetMap data.
This is a [plugin](http://wiki.openstreetmap.org/wiki/Osmosis/WritingPlugins) that can be used by Osmosis to 
import OSM data in a Neo4j-Spatial database.

If you want to play with it: 

1. [download Osmosis](http://dev.openstreetmap.org/~bretth/osmosis-build/osmosis-bin-latest.zip).
2. Create a neo4j-osmosis.zip containing compiled classes and src/plugin.xml of this project and copy 
it in a [directory where Osmosis can find it](http://wiki.openstreetmap.org/wiki/Osmosis/Detailed_Usage#Plugin_Tasks), like
`$HOME/.openstreetmap/osmosis/plugins` in OSX or Linux.
3. Put the following libs in `osmosis/lib/default`:


* jts-1.10.jar
* geoapi-2.3-M1.jar
* gt-api-2.6.3.jar
* gt-main-2.6.3.jar
* gt-metadata-2.6.3.jar
* gt-data-2.6.3.jar
* jta-1.1.jar
* neo4j-kernel-1.0.jar
* neo4j-commons-1.0.jar
* neo4j-spatial.jar

Then you can execute, for example:

	./osmosis --read-xml-0.6 file=ireland.osm --write-neo4j dir=neo4j-db
