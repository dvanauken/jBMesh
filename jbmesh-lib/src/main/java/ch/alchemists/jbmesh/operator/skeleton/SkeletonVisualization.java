// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import ch.alchemists.jbmesh.lookup.ExactHashDeduplication;
import ch.alchemists.jbmesh.structure.BMesh;
import ch.alchemists.jbmesh.structure.Vertex;
import ch.alchemists.jbmesh.util.PlanarCoordinateSystem;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkeletonVisualization {
    private static final Logger logger = LoggerFactory.getLogger(SkeletonVisualization.class);

    public static class VisNode {
        public final Vector3f pos = new Vector3f();
        public final String name;

        public VisNode(String name) {
            this.name = name;
        }
    }


    private final PlanarCoordinateSystem coordSys;
    private final ArrayList<SkeletonNode> initialNodes ;
    private final SkeletonContext ctx;


    SkeletonVisualization(PlanarCoordinateSystem coordSys, ArrayList<SkeletonNode> initialNodes, SkeletonContext ctx) {
        this.coordSys = coordSys;
        this.initialNodes = initialNodes;
        this.ctx = ctx;
        logger.debug("SkeletonVisualization initialized with {} initial nodes", initialNodes.size());
    }


    public BMesh createSkeletonMappingVis() {
        logger.info("Creating skeleton mapping visualization");
        return createStraightSkeletonVis(SkeletonNode.EdgeType.Mapping);
    }

    public BMesh createSkeletonDegeneracyVis() {
        logger.info("Creating skeleton degeneracy visualization");
        return createStraightSkeletonVis(SkeletonNode.EdgeType.Degeneracy);
    }

    private BMesh createStraightSkeletonVis(SkeletonNode.EdgeType edgeType) {
        BMesh bmesh = new BMesh();
        ExactHashDeduplication dedup = new ExactHashDeduplication(bmesh);
        Set<SkeletonNode> nodesDone = new HashSet<>();

        for(SkeletonNode node : initialNodes) {
            straightSkeletonVis_addEdge(bmesh, dedup, nodesDone, node, edgeType);
        }

        //System.out.println("Straight Skeleton Visualization: " + nodesDone.size() + " unique nodes");
        logger.debug("Straight Skeleton Visualization created with {} unique nodes", nodesDone.size());
        return bmesh;
    }

    private boolean isInvalid(Vector2f v) {
        return Float.isNaN(v.x) || Float.isInfinite(v.x);
    }

    private Vertex getVertex(ExactHashDeduplication dedup, Vector2f v) {
        Vector2f pos = new Vector2f(v);
        if(isInvalid(pos)) {
            pos.set(-50, -50);
        }
        return dedup.getOrCreateVertex(coordSys.unproject(pos));
    }

    private void straightSkeletonVis_addEdge(BMesh bmesh, ExactHashDeduplication dedup, Set<SkeletonNode> nodesDone, SkeletonNode src, SkeletonNode.EdgeType edgeType) {
        if(!nodesDone.add(src)) {
            //System.out.println("straightSkeletonVis: node duplicate at " + coordSys.unproject(src.p));
            logger.trace("Node duplicate detected at {}", coordSys.unproject(src.p));
            return;
        }

        Vertex v0 = getVertex(dedup, src.p);
        for(Map.Entry<SkeletonNode, SkeletonNode.EdgeType> entry : src.outgoingEdges.entrySet()) {
            SkeletonNode target = entry.getKey();
            if(entry.getValue() == edgeType) {
                Vertex v1 = getVertex(dedup, target.p);
                if(v0 != v1 && v0.getEdgeTo(v1) == null) {
                    bmesh.createEdge(v0, v1);
                    logger.trace("Edge created between {} and {}", v0, v1);
                }
            }

            straightSkeletonVis_addEdge(bmesh, dedup, nodesDone, target, edgeType);
        }
    }


    public BMesh createMovingNodesVis() {
        logger.info("Creating moving nodes visualization");
        BMesh bmesh = new BMesh();
        Set<MovingNode> nodesRemaining = new HashSet<>(ctx.getNodes());

        while(!nodesRemaining.isEmpty()) {
            Optional<MovingNode> any = nodesRemaining.stream().findAny();
            createMovingNodesVis(bmesh, any.get(), nodesRemaining);
        }
        logger.debug("Moving nodes visualization created");
        return bmesh;
    }

    private void createMovingNodesVis(BMesh bmesh, MovingNode startNode, Set<MovingNode> nodesRemaining) {
        List<Vertex> vertices = new ArrayList<>();

        MovingNode current = startNode;
        do {
            Vertex v = bmesh.createVertex( coordSys.unproject(current.skelNode.p) );
            vertices.add(v);

            nodesRemaining.remove(current);
            current = current.next;
        } while(current != startNode);

        for(int i=0; i<vertices.size(); ++i) {
            int nextIndex = (i+1) % vertices.size();
            bmesh.createEdge(vertices.get(i), vertices.get(nextIndex));
        }
        logger.trace("Created polygon with {} vertices", vertices.size());
    }


    public List<VisNode> getMovingNodes() {
        logger.info("Getting moving nodes for visualization");
        List<VisNode> nodes = new ArrayList<>();
        for(MovingNode movingNode : ctx.getNodes()) {
            VisNode node = new VisNode(movingNode.id);
            coordSys.unproject(movingNode.skelNode.p, node.pos);
            nodes.add(node);
        }
        logger.debug("Retrieved {} moving nodes", nodes.size());
        return nodes;
    }


    public BMesh createBisectorVis() {
        logger.info("Creating bisector visualization");
        BMesh bmesh = new BMesh();

        for(MovingNode movingNode : ctx.getNodes()) {
            Vector2f p0 = movingNode.skelNode.p;
            Vector2f p1 = movingNode.bisector.mult(0.33f).addLocal(p0);

            Vertex v0 = bmesh.createVertex( coordSys.unproject(p0) );
            Vertex v1 = bmesh.createVertex( coordSys.unproject(p1) );

            bmesh.createEdge(v0, v1);
        }
        logger.debug("Bisector visualization created with {} bisectors", ctx.getNodes().size());
        return bmesh;
    }


    public BMesh createMappingVis() {
        logger.info("Creating mapping visualization");
        BMesh bmesh = new BMesh();

        List<SkeletonNode> targets = new ArrayList<>();

        for(SkeletonNode initial : initialNodes) {
            targets.clear();
            initial.followGraphInward(targets);

            Vertex v0 = bmesh.createVertex( coordSys.unproject(initial.p) );
            for(SkeletonNode target : targets) {
                Vertex v1 = bmesh.createVertex( coordSys.unproject(target.p) );
                bmesh.createEdge(v0, v1);
            }
            logger.trace("Created mapping for initial node with {} targets", targets.size());
        }

        logger.debug("Mapping visualization created for {} initial nodes", initialNodes.size());
        return bmesh;
    }
}
