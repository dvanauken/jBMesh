// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class SkeletonEvent implements Comparable<SkeletonEvent> {
    private static final Logger logger = LoggerFactory.getLogger(SkeletonEvent.class);
    public static final float INVALID_TIME = Float.NaN;
    public final float time; // Always positive


    protected SkeletonEvent(float time) {
        this.time = time;
        logger.debug("Created {} at time {}", this.getClass().getSimpleName(), time);
    }


    @Override
    public int compareTo(SkeletonEvent other) {
        if(this.time < other.time)
            return -1;
        if(this.time > other.time)
            return 1;

        if(this instanceof EdgeEvent && other instanceof SplitEvent)
            return -1;
        if(this instanceof SplitEvent && other instanceof EdgeEvent)
            return 1;

        // A TreeSet as event queue doesn't allow duplicate keys (time values).
        // Compare the hashes so compareTo() won't return 0 if events happen at the same time.
        return Integer.compare(hashCode(), other.hashCode());
    }


    public abstract void onEventQueued();
    public abstract void onEventAborted(MovingNode adjacentNode, SkeletonContext ctx);
    public abstract void onEventAborted(MovingNode edgeNode0, MovingNode edgeNode1, SkeletonContext ctx);

    public abstract void handle(SkeletonContext ctx);


    /**
     * Always aborts events of 'node'.
     */
    protected static void handle(MovingNode node, SkeletonContext ctx) {
        logger.debug("Handling event for node {}", node.id);
        //System.out.println("handle " + node);
        while(ensureValidPolygon(node, ctx)) {
            boolean validBisector = node.calcBisector(ctx);
            if(validBisector) {
                logger.trace("Valid bisector calculated for node {}", node.id);
                node.leaveSkeletonNode();

                node.updateEdge();
                node.prev.updateEdge();

                createEvents(node, ctx);
                return;
            }
            logger.debug("Handling degenerate angle for node {}", node.id);
            node = handleDegenerateAngle(node, ctx);
        }
    }


    static void handleInit(MovingNode node, SkeletonContext ctx) {
        logger.debug("Initializing handling for node {}", node.id);
        while(ensureValidPolygon(node, ctx)) {
            boolean validBisector = node.calcBisector(ctx);
            if(validBisector) {
                logger.trace("Valid bisector calculated during initialization for node {}", node.id);
                return;
            }
            logger.debug("Handling degenerate angle during initialization for node {}", node.id);
            node = handleDegenerateAngle(node, ctx);
        }
    }


    /**
     * Check for valid polygon and handle case in which a polygon degenerates to a line.
     * @return True if polygon consists of >2 edges.
     */
    private static boolean ensureValidPolygon(MovingNode node, SkeletonContext ctx) {
        MovingNode next = node.next;
        assert next != node;

        if(next != node.prev)
            return true;

        // Degenerated polygon
        logger.info("Polygon degenerated to a line for node {}", node.id);
        node.skelNode.addDegenerationEdge(next.skelNode);
        ctx.removeMovingNode(node);
        ctx.removeMovingNode(next);

        return false;
    }


    private static void createEvents(MovingNode node, SkeletonContext ctx) {
        logger.debug("Creating events for node {}", node.id);
        ctx.abortEvents(node);

        // Create edge events if edge is shrinking
        ctx.tryQueueEdgeEvent(node, node.next);
        ctx.tryQueueEdgeEvent(node.prev, node);

        createAllSplitEvents(node, ctx);
    }


    // TODO: Test reflex nodes against all edges in other loops too? That would allow multiple disconnected initial loops.
    /**
     * Tests adjacent edges of 'node' against other eligible reflex vertices in MovingNodes-loop.
     * If 'node' is reflex, tests it against all eligible edges.
     * Eligible tests: Minimum distance between reflex node and candidate edge = 2 edges in between
     *
     * A triangle cannot be concave. A concave quadrilateral (arrowhead) doesn't need split events.
     * Minimum vertices for split events = 5.
     */
    private static void createAllSplitEvents(MovingNode node, SkeletonContext ctx) {
        logger.debug("Creating all split events for node {}", node.id);
        MovingNode current = node.next.next;   // processed in first step before loop
        final MovingNode end = node.prev.prev; // excluded from loop, but processed in last step after loop

        // Ignore triangles and quads
        if(current == end.next || current == end) {
            logger.trace("Skipping split events for node {} (triangle or quad)", node.id);
            return;
        }

        final boolean nodeIsReflex = node.isReflex();
        SplitEvent nearestSplit = null;

        // First step: Test 'current' vertex only against first adjacent edge (node.prev->node).
        //             Test 'node' against current edge.
        if(current.isReflex()) {
            logger.trace("Creating split event for reflex node {}", current.id);
            ctx.tryQueueSplitEvent(current, node.prev, node);
        }

        if(nodeIsReflex)
            nearestSplit = ctx.tryReplaceNearestSplitEvent(node, current, current.next, nearestSplit);

        // Intermediate steps, all tests
        current = current.next;
        for(; current != end; current = current.next) {
            if(current.isReflex()) {
                logger.trace("Creating split events for reflex node {}", current.id);
                ctx.tryQueueSplitEvent(current, node, node.next);
                ctx.tryQueueSplitEvent(current, node.prev, node);
            }

            if(nodeIsReflex) // Condition is constant. Manual optimization (loop unswitching) not worth it.
                nearestSplit = ctx.tryReplaceNearestSplitEvent(node, current, current.next, nearestSplit);
        }

        // Last step: Test 'current' only against second adjacent edge (node->node.next)
        //            Don't test "nodeIsReflex" against this last edge.
        if(current.isReflex()) {
            logger.trace("Creating final split event for reflex node {}", current.id);
            ctx.tryQueueSplitEvent(current, node, node.next);
        }

        if(nearestSplit != null) {
            logger.debug("Enqueueing nearest split event for node {}", node.id);
            ctx.enqueue(nearestSplit);
        }
    }


    static void createSplitEvents(MovingNode reflexNode, SkeletonContext ctx) {
        logger.debug("Creating split events for reflex node {}", reflexNode.id);
        MovingNode current = reflexNode.next.next;
        MovingNode end = reflexNode.prev.prev; // exclusive

        // Ignore triangles, quads will also be ignored by the loop condition below
        if(current == end.next) {
            logger.trace("Skipping split events for reflex node {} (triangle)", reflexNode.id);
            return;
        }

        SplitEvent nearestSplit = null;
        for(; current != end; current = current.next)
            nearestSplit = ctx.tryReplaceNearestSplitEvent(reflexNode, current, current.next, nearestSplit);

        if(nearestSplit != null) {
            logger.debug("Enqueueing nearest split event for reflex node {}", reflexNode.id);
            ctx.enqueue(nearestSplit);
        }
    }


    private static MovingNode handleDegenerateAngle(MovingNode node, SkeletonContext ctx) {
        logger.info("Handling degenerate angle for node {}", node.id);

        // Remove node, connect node.prev <-> node.next
        MovingNode o1 = node.prev;
        MovingNode o2 = node.next;
        assert o1.next == node;
        assert o2.prev == node;
        o1.next = o2;
        o2.prev = o1;

        MovingNode connectionTarget;
        if(node.skelNode.p.distanceSquared(o1.skelNode.p) < node.skelNode.p.distanceSquared(o2.skelNode.p))
            connectionTarget = o1;
        else
            connectionTarget = o2;

        node.skelNode.addDegenerationEdge(connectionTarget.skelNode);
        ctx.removeMovingNode(node);

        logger.debug("Degenerate angle handled, connection target: {}", connectionTarget.id);
        return connectionTarget;
    }
}
