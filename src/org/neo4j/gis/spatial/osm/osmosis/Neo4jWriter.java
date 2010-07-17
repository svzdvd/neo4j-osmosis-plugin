package org.neo4j.gis.spatial.osm.osmosis;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.gis.spatial.Constants;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;


/**
 * see: 
 * http://svn.openstreetmap.org/applications/utils/export/osm2pgsql/README.txt
 * http://wiki.openstreetmap.org/wiki/Osm2pgsql
 * 
 * @author Davide Savazzi
 */
public class Neo4jWriter implements Sink, Constants {

	// Constructor
	
	public Neo4jWriter(File dir, String layerNamePrefix, int commitInterval) {
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new IllegalArgumentException("Cannot create directory " + dir.getAbsolutePath());
			}
		}

		if (!dir.isDirectory()) {
			throw new IllegalArgumentException("Not a directory " + dir.getAbsolutePath());
		}
		
		GraphDatabaseService graphDatabase = new EmbeddedGraphDatabase(dir.getAbsolutePath());
		spatialDatabase = new SpatialDatabaseService(graphDatabase);
		
		tx = graphDatabase.beginTx();
		try {
			pointsLayer = spatialDatabase.getOrCreateLayer(layerNamePrefix + " points");
			geometryFactory = pointsLayer.getGeometryFactory();
			
			this.layerIndex.put(layerNamePrefix + " points", pointsLayer);
			
			tx.success();
		} finally {
			tx.finish();
			tx = null;
		}
		
		this.layerNamePrefix = layerNamePrefix;
		
		this.commitCounter = 0;
		this.totalCommitCounter = 0;
		this.commitInterval = commitInterval;
		
		this.nodeNotFoundCounter = 0;
		
		this.start = System.currentTimeMillis();
	}
	
	
	// Public methods
		
	public void process(EntityContainer entityContainer) {
		if (tx == null) {
			tx = spatialDatabase.getDatabase().beginTx();
		}
		
		Entity entity = entityContainer.getEntity();
		if (entity instanceof Node) {
			onNode((Node) entity);
		} else if (entity instanceof Way) {
			onWay((Way) entity);
		} else if (entity instanceof Relation) {
			onRelation((Relation) entity);
		} 		
		
		commitCounter++;
		totalCommitCounter++;
		if (commitCounter >= commitInterval) {
			tx.success();
			tx.finish();
			tx = null;
			commitCounter = 0;
			
			System.out.println("inserted " + totalCommitCounter + " entities so far");
		}
	}
	
	public void complete() {
		for (String layerName : layerProperties.keySet()) {
			Layer layer = layerIndex.get(layerName);
			String[] properties = layerProperties.get(layerName).toArray(new String[] {});
			layer.mergeExtraPropertyNames(properties);
		}
		
		tx.success();
		tx.finish();		
		
		long stop = System.currentTimeMillis();
		
		System.out.println("import completed in " + (stop - start) + "ms");
		System.out.println("nodes not found: " + nodeNotFoundCounter);
	}

	public void release() {
		spatialDatabase.getDatabase().shutdown();
		System.out.println("database closed");		
	}

	
	// Private methods
	
	private void onNode(Node node) {
		nodeIndex.put(node.getId(), new LightNode(node.getId(), node.getLongitude(), node.getLatitude()));
		
		if (nodeIsPoint(node)) {
			readFields(node);
			addFieldsNameToLayer("points", fieldsName);
				
			pointsLayer.add(
				geometryFactory.createPoint(new Coordinate(node.getLongitude(), node.getLatitude())), 
				fieldsName.toArray(new String[fieldsName.size()]), 
				fields.toArray(new Object[fields.size()]));			
		}
	}

	private void onWay(Way way) {
		if (way.getWayNodes().size() == 0) return;
		
		OsmStyleRegistry.Style style = getStyle(way);
		if (style != null) {
			Geometry geom = null;
			if (style.geomType == GTYPE_LINESTRING || style.geomType == GTYPE_POLYGON) {
				geom = buildGeometry(way);
			}
			
			if (geom != null) {
				readFields(way);
				addFieldsNameToLayer(style.tag, fieldsName);
				
				Layer layer = getLayer(style.tag);
				layer.add(
					geom, 
					fieldsName.toArray(new String[fieldsName.size()]), 
					fields.toArray(new Object[fields.size()]));				
			}
		}
	}
	
	private Layer getLayer(String tag) {
		if (!layerIndex.containsKey(layerNamePrefix + " " + tag)) {
			layerIndex.put(layerNamePrefix + " " + tag, spatialDatabase.getOrCreateLayer(layerNamePrefix + " " + tag));
		}
		
		return layerIndex.get(layerNamePrefix + " " + tag);
	}
	
	private void addFieldsNameToLayer(String tag, List<String> names) {
		Set<String> namesIndex = layerProperties.get(layerNamePrefix + " " + tag);
		if (namesIndex == null) {
			namesIndex = new HashSet<String>();
			layerProperties.put(layerNamePrefix + " " + tag, namesIndex);
		}
		
		for (String name : names) {
			namesIndex.add(name);
		}
	}
	
	private Geometry buildGeometry(Way way) {
		Coordinate[] coordinates = getCoordinates(way);
		if (coordinates == null) return null;
		
		if (coordinates.length == 1) {
			return geometryFactory.createPoint(coordinates[0]);
		} else if (coordinates.length > 3 && coordinates[0].equals(coordinates[coordinates.length - 1])) {
			return geometryFactory.createPolygon(geometryFactory.createLinearRing(coordinates), null);
		} else {
			return geometryFactory.createLineString(coordinates);
		}
	}
	
	private Coordinate[] getCoordinates(Way way) {
		List<Coordinate> coordinates = new ArrayList<Coordinate>(way.getWayNodes().size());
		for (WayNode node : way.getWayNodes()) {
			LightNode lnode = nodeIndex.get(node.getNodeId());
			if (lnode != null) {
				coordinates.add(new Coordinate(lnode.x, lnode.y));				
			} else {
				System.err.println("node NOT found: " + node.getNodeId());
				nodeNotFoundCounter++;
			}
		}
		
		if (coordinates.size() == 0) {
			return null;
		} else {
			return coordinates.toArray(new Coordinate[coordinates.size()]);
		}
	}
	
	/**
	 * The relations are parsed. Osm2pgsql has special handling for a
 	 * limited number of types: multipolygon, route, boundary
	 */
	private void onRelation(Relation relation) {
		// TODO
	}
	
	/**
	 * return first style found
	 */
	private OsmStyleRegistry.Style getStyle(Entity entity) {
		OsmStyleRegistry.Style areaStyle = null;
		
		for (Tag tag : entity.getTags()) {
			OsmStyleRegistry.Style style = styleIndex.getStyle(tag.getKey());
			if (style != null && 
				((entity instanceof Node && style.osmTypes.indexOf("node") != -1) ||
				 (entity instanceof Way && style.osmTypes.indexOf("way") != -1))) {
				if (style.tag.equals("area")) {
					areaStyle = style;
				} else {
					return style;
				}
			}
		}
		
		// if no other styles have been found
		return areaStyle;
	}
	
	/**
	 * If a node has a tag declared in the style file then it is 
	 * added to planet_osm_point. If it has no such tag then
	 * the position is noted, but not added to the database	
	 */
	private boolean nodeIsPoint(Node node) {
		OsmStyleRegistry.Style style = getStyle(node);
		return style != null && !style.flags.equals("delete");
	}	
	
	private void readFields(Entity entity) {
		fieldsName.clear();
		fields.clear();
		
		fieldsName.add("osm_id");
		fields.add(entity.getId());
		
		for (Tag tag : entity.getTags()) {
			if (!"created_by".equals(tag.getKey()) && !"source".equals(tag.getKey())) {			
				fieldsName.add(tag.getKey());
				fields.add(tag.getValue());
			}
		}
	}
	
	
	// Attributes
	
	private long start;
	
	private OsmStyleRegistry styleIndex = new OsmStyleRegistry();
	private Map<Long,LightNode> nodeIndex = new HashMap<Long,LightNode>();
	private SpatialDatabaseService spatialDatabase;
	
	private String layerNamePrefix;
	private Layer pointsLayer;
	private GeometryFactory geometryFactory;
	
	private Map<String,Layer> layerIndex = new HashMap<String,Layer>();
	private Map<String,Set<String>> layerProperties = new HashMap<String,Set<String>>();
	
	private int nodeNotFoundCounter;
	private int commitCounter;
	private int totalCommitCounter;
	private int commitInterval;
	private Transaction tx;
	
	private List<String> fieldsName = new ArrayList<String>();
	private List fields = new ArrayList();
	
	class LightNode {
		
		// Constructor
		
		public LightNode(long id, double x, double y) {
			this.id = id;
			this.x  = x;
			this.y = y;
		}
		
		
		// Attributes
		
		public long id;
		public double x;
		public double y;
	}
}