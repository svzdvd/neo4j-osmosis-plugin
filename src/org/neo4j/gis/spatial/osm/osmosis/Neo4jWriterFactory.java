package org.neo4j.gis.spatial.osm.osmosis;

import java.io.File;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;


/**
 * @author Davide Savazzi
 */
public class Neo4jWriterFactory extends TaskManagerFactory {

	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
		String fileName = getStringArgument(taskConfig, 
				"dir", 
				getDefaultStringArgument(taskConfig, "neo4j-db"));
		
		String layerName = getStringArgument(taskConfig, 
				"layer",
				getDefaultStringArgument(taskConfig, "osm"));
		
		int commitInterval = getIntegerArgument(taskConfig,
				"commitInterval",
				getDefaultIntegerArgument(taskConfig, 10000));
		
		File dir = new File(fileName);
		Sink sink = new Neo4jWriter(dir, layerName, commitInterval);
		return new SinkManager(taskConfig.getId(), sink, taskConfig.getPipeArgs()); 
	}
}
