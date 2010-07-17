package org.neo4j.gis.spatial.osm.osmosis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.neo4j.gis.spatial.Constants;


/**
 * @author Davide Savazzi
 */
public class OsmStyleRegistry implements Constants {

	// Constructor
	
	public OsmStyleRegistry() {
		try {
			InputStream stream = getClass().getResourceAsStream("/org/neo4j/gis/spatial/osm/osmosis/default.style");
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			
			String line;
			while ((line = reader.readLine()) != null) {
				StringTokenizer tokenizer = new StringTokenizer(line, " ");
				if (tokenizer.countTokens() >= 4) {
					Style s = new Style();
					s.osmTypes = tokenizer.nextToken();
					s.tag = tokenizer.nextToken();
					s.dataType = tokenizer.nextToken();
					s.flags = tokenizer.nextToken();
					
					if (!s.osmTypes.startsWith("#")) {
						if (s.flags.indexOf("linear") != -1) {
							s.geomType = GTYPE_LINESTRING;
						}
	
						if (s.flags.indexOf("polygon") != -1) {
							s.geomType = GTYPE_POLYGON;
						}

						styleIndex.put(s.tag, s);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Style getStyle(String tag) {
		return styleIndex.get(tag);
	}
		
	
	// Attributes
	
	private Map<String,Style> styleIndex = new HashMap();
	
	
	// Private classes
	
	class Style {
		String osmTypes;
		String tag;
		String dataType;
		String flags;
		int geomType = -1;
	}
}