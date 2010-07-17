package org.neo4j.gis.spatial.osm.osmosis;

import java.util.HashMap;
import java.util.Map;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.plugin.PluginLoader;


/**
 * @author Davide Savazzi
 */
public class Neo4jPlugin implements PluginLoader {

	public Map<String, TaskManagerFactory> loadTaskFactories() {
		HashMap<String, TaskManagerFactory> map = new HashMap<String, TaskManagerFactory>();
		map.put("write-neo4j", new Neo4jWriterFactory());
		return map;
	}

}
