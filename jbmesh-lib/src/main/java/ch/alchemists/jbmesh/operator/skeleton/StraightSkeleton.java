// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import ch.alchemists.jbmesh.data.BMeshAttribute;
import ch.alchemists.jbmesh.data.property.Vec3Attribute;
import ch.alchemists.jbmesh.structure.BMesh;
import ch.alchemists.jbmesh.structure.Face;
import ch.alchemists.jbmesh.structure.Vertex;
import ch.alchemists.jbmesh.util.PlanarCoordinateSystem;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StraightSkeleton {

    private static final Logger logger = LoggerFactory.getLogger(StraightSkeleton.class);

    private final Vec3Attribute<Vertex> positions;

    private float offsetDistance = Float.POSITIVE_INFINITY; // Absolute value
    private float distanceSign = -1.0f;

    private PlanarCoordinateSystem coordSys;
    private final ArrayList<SkeletonNode> initialNodes = new ArrayList<>();
    private final SkeletonContext ctx = new SkeletonContext();


    public StraightSkeleton(BMesh bmesh) {
        logger.info("Initializing StraightSkeleton with BMesh");
        positions = Vec3Attribute.get(BMeshAttribute.Position, bmesh.vertices());
    }


    /**
     * Sets the absolute distance in units by which the edges should be moved.<br>
     * Positive: Grow face. Negative: Shrink face.<br>
     * Defaults to Float.NEGATIVE_INFINITY.
     * @param distance
     */
    public void setDistance(float distance) {
        logger.debug("Setting distance to {}", distance);
        if(distance == Float.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("Cannot scale outwards to infinity.");
        }

        distanceSign = Math.signum(distance);
        offsetDistance = Math.abs(distance);
        logger.debug("Distance sign: {}, Offset distance: {}", distanceSign, offsetDistance);
    }


    /**
     * Sets the epsilon value that is used for degeneracy tests. Bigger values may reduce errors due to numerical instability in certain cases.
     * Defaults to 0.0001f.
     * @param epsilon
     */
    public void setEpsilon(float epsilon) {
        logger.debug("Setting epsilon to {}", epsilon);
        ctx.setEpsilon(epsilon);
    }


    public void apply(Face face) {
        logger.info("Applying straight skeleton to face with {} vertices", face.getVertices().size());
        //logger.info("Applying StraightSkeleton to face");
        List<Vertex> vertices = face.getVertices();
        logger.debug("Face has {} vertices", vertices.size());
        assert vertices.size() >= 3;

        ctx.reset(offsetDistance, distanceSign);
        coordSys = new PlanarCoordinateSystem().forFace(face, positions);

        float diagonalSize = createNodes(vertices);
        logger.debug("Created nodes. Diagonal size: {}", diagonalSize);

        // When shrinking to infinity, use polygon's bounding rectangle to determine max distance (less events queued = speed up)
        if(distanceSign < 0 && offsetDistance == Float.POSITIVE_INFINITY) {
            ctx.distance = diagonalSize * 0.51f;
            logger.debug("Adjusted distance for infinite shrinking: {}", ctx.distance);
        }

        if(ctx.distance != 0) {
            initBisectors();
            initEvents();
            loop();
        }
        else{
            logger.info("Skipping main loop as distance is 0");
        }
    }


    private void loop() {
        logger.info("Starting main loop");
        ctx.time = 0;
        int eventCount = 0;

        while(true) {
            //ctx.printNodes();
            //ctx.printEvents();

            SkeletonEvent event = ctx.pollQueue();
            if(event == null) {
                logger.debug("No more events. Scaling by {}", ctx.distance - ctx.time);
                scale(ctx.distance - ctx.time);
                break;
            }

            scale(event.time - ctx.time);
            ctx.time = event.time;
            logger.debug("Processing event at time {}. Event type: {}", ctx.time, event.getClass().getSimpleName());
            event.handle(ctx);
            ctx.recheckAbortedReflexNodes();
            eventCount++;
        }
        logger.info("Main loop completed. Processed {} events", eventCount);
    }


    /**
     * Creates MovingNodes for all the vertices.
     * Also calculates bounding rectangle.
     * @return Diagonal length of bounding rectangle.
     */
    private float createNodes(List<Vertex> vertices) {
        logger.debug("Creating nodes for {} vertices", vertices.size());
        initialNodes.clear();
        initialNodes.ensureCapacity(vertices.size());

        Vector3f min = Vector3f.POSITIVE_INFINITY.clone();
        Vector3f max = Vector3f.NEGATIVE_INFINITY.clone();

        Vector3f vertexPos = new Vector3f();
        final MovingNode first = createNode(vertices.get(0), vertexPos, min, max);
        MovingNode last = first;

        for(int i=1; i<vertices.size(); ++i) {
            MovingNode movingNode = createNode(vertices.get(i), vertexPos, min, max);

            // Link nodes
            movingNode.prev = last;
            last.next = movingNode;

            last = movingNode;
        }

        // Link last node with first
        first.prev = last;
        last.next = first;

        float diagonalLength = max.subtractLocal(min).length();
        logger.debug("Nodes created. Bounding box diagonal length: {}", diagonalLength);
        return diagonalLength;
    }

    private MovingNode createNode(Vertex vertex, Vector3f vertexPos, Vector3f min, Vector3f max) {
        positions.get(vertex, vertexPos);
        min.minLocal(vertexPos);
        max.maxLocal(vertexPos);

        SkeletonNode initialNode = new SkeletonNode();
        coordSys.project(vertexPos, initialNode.p);
        initialNodes.add(initialNode);

        MovingNode movingNode = ctx.createMovingNode();
        movingNode.skelNode = initialNode;
        return movingNode;
    }


    private void initBisectors() {
        logger.debug("Initializing bisectors");
        List<MovingNode> degenerates = new LinkedList<>();

        for(MovingNode node : ctx.getNodes()) {
            boolean validBisector = node.calcBisector(ctx, true);
            if(!validBisector)
                degenerates.add(node);
        }

        // Process degenerate nodes after all bisectors have been initialized
        logger.debug("Found {} degenerate nodes", degenerates.size());
        for(MovingNode degenerateNode : degenerates) {
            // Check if 'degenerateNode' was already removed in previous handleInit() calls
            if(degenerateNode.next != null)
                SkeletonEvent.handleInit(degenerateNode, ctx);
        }
    }


    private void initEvents() {
        logger.debug("Initializing events");
        List<MovingNode> reflexNodes = new LinkedList<>();

        for(MovingNode current : ctx.getNodes()) {
            current.leaveSkeletonNode();

            current.updateEdge();
            ctx.tryQueueEdgeEvent(current, current.next);

            if(current.isReflex())
                reflexNodes.add(current);
        }

        // Process the reflex nodes after all edges have been initialized with updateEdge().
        logger.debug("Found {} reflex nodes", reflexNodes.size());
        for(MovingNode reflex : reflexNodes)
            SkeletonEvent.createSplitEvents(reflex, ctx);
    }


    private void scale(float dist) {
        if(dist == 0)
            return;

        logger.debug("Scaling by distance {}", dist);
        Vector2f dir = new Vector2f();

        for(MovingNode node : ctx.getNodes()) {
            dir.set(node.bisector).multLocal(dist);
            node.skelNode.p.addLocal(dir);

            if(isInvalid(node.skelNode.p)) {
                logger.warn("Invalid position after scale: bisector={}, dir={}", node.bisector, dir);
            }

            //assert !isInvalid(node.skelNode.p) : "Invalid position after scale: bisector=" + node.bisector + ", dir=" + dir;
        }
    }


    private boolean isInvalid(Vector2f v) {
        return Float.isNaN(v.x) || Float.isInfinite(v.x);
    }


    //
    // Results
    //

    public List<SkeletonNode> getStartNodes() {
        return Collections.unmodifiableList(initialNodes);
    }

    public List<SkeletonNode> getEndNodes() {
        Set<MovingNode> movingNodes = ctx.getNodes();
        List<SkeletonNode> skelNodes = new ArrayList<>(movingNodes.size());

        for(MovingNode movingNode : movingNodes)
            skelNodes.add(movingNode.skelNode);

        return skelNodes;
    }

    public List<List<SkeletonNode>> getNodeLoops() {
        Set<MovingNode> nodes = new HashSet<>(ctx.getNodes());
        List<List<SkeletonNode>> nodeLoops = new ArrayList<>(2);

        // TODO: Make a generic class that finds such loops? (also for edge loops)
        while(!nodes.isEmpty()) {
            List<SkeletonNode> loop = new ArrayList<>(4);
            nodeLoops.add(loop);

            final MovingNode start = nodes.stream().findFirst().get();
            MovingNode current = start;
            do {
                loop.add(current.skelNode);
                nodes.remove(current);
                current = current.next;
            } while(current != start);
        }

        return nodeLoops;
    }


    public Vector3f getPosition(SkeletonNode node) {
        return coordSys.unproject(node.p);
    }

    public Vector3f getPosition(SkeletonNode node, Vector3f store) {
        return coordSys.unproject(node.p, store);
    }


    public SkeletonVisualization getVisualization() {
        return new SkeletonVisualization(coordSys, initialNodes, ctx);
    }
}
