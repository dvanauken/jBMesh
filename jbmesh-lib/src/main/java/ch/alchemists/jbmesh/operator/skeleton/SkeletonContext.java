// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class SkeletonContext {
    private static final Logger logger = LoggerFactory.getLogger(SkeletonContext.class);
    private int nextMovingNodeId = 1;

    private final LinkedHashSet<MovingNode> movingNodes = new LinkedHashSet<>();
    private final TreeSet<SkeletonEvent> eventQueue = new TreeSet<>(); // Must support sorting, fast add & remove, poll lowest

    // Contains reflex nodes of aborted SplitEvents. Since we only enqueue the nearest SplitEvent to reduce strain on the queue,
    // when a SplitEvent is aborted we must recheck if a reflex node collides with another edge that was originally further away.
    private final Set<MovingNode> abortedReflex = new HashSet<>();

    public float distance;
    public float distanceSign;
    public float time = 0;

    public float epsilon = 0.0001f;
    public float epsilonMinusOne = epsilon - 1f; // -0.9999


    SkeletonContext() {
        logger.info("Initializing SkeletonContext");
    }


    public void setEpsilon(float epsilon) {
        logger.debug("Setting epsilon to {}", epsilon);
        this.epsilon = epsilon;
        this.epsilonMinusOne = epsilon - 1f;
    }


    public Set<MovingNode> getNodes() {
        return Collections.unmodifiableSet(movingNodes);
    }


    public void reset(float distance, float distanceSign) {
        logger.info("Resetting SkeletonContext with distance: {}, distanceSign: {}", distance, distanceSign);
        this.distance = distance;
        this.distanceSign = distanceSign;
        time = 0;

        nextMovingNodeId = 1;

        movingNodes.clear();
        eventQueue.clear();
        abortedReflex.clear();
    }


    //
    // Moving Nodes
    //

    public MovingNode createMovingNode() {
        MovingNode node = createMovingNode(Integer.toString(nextMovingNodeId));
        nextMovingNodeId++;
        logger.debug("Created MovingNode with ID: {}", node.id);
        return node;
    }

    public MovingNode createMovingNode(String id) {
        MovingNode node = new MovingNode(id);
        movingNodes.add(node);
        logger.debug("Created MovingNode with custom ID: {}", id);
        return node;
    }

    protected void removeMovingNode(MovingNode node) {
        logger.debug("Removing MovingNode: {}", node.id);
        node.next = null;
        node.prev = null;
        abortEvents(node);
        movingNodes.remove(node);
    }


    //
    // Event Queue
    //

    public SkeletonEvent pollQueue() {
        SkeletonEvent event = eventQueue.pollFirst();
        if (event != null) {
            logger.debug("Polled event from queue: {} at time {}", event.getClass().getSimpleName(), event.time);
        }
        return event;
    }

    public void enqueue(SkeletonEvent event) {
        assert event.time >= time : "time: " + time + ", event.time: " + event.time + " // " + event;

        boolean added = eventQueue.add(event);
        assert added;
        event.onEventQueued();
        logger.debug("Enqueued event: {} at time {}", event.getClass().getSimpleName(), event.time);
    }

    public void addAbortedReflex(MovingNode reflexNode) {
        logger.debug("Adding aborted reflex node: {}", reflexNode.id);
        abortedReflex.add(reflexNode);
    }


    /**
     * Node invalidated.
     */
    public void abortEvents(MovingNode adjacentNode) {
        logger.debug("Aborting events for adjacent node: {}", adjacentNode.id);
        for(SkeletonEvent event : adjacentNode.events()) {
            event.onEventAborted(adjacentNode, this);
            eventQueue.remove(event);
            logger.trace("Aborted event: {}", event.getClass().getSimpleName());
        }
        adjacentNode.clearEvents();
    }

    /**
     * Edge invalidated.
     */
    public void abortEvents(MovingNode edgeNode0, MovingNode edgeNode1) {
        logger.debug("Aborting events for edge between nodes: {} and {}", edgeNode0.id, edgeNode1.id);
        // Abort all events that exist in both edgeNode0's and edgeNode1's list
        for(Iterator<SkeletonEvent> it0=edgeNode0.events().iterator(); it0.hasNext(); ) {
            SkeletonEvent event = it0.next();
            if(edgeNode1.tryRemoveEvent(event)) {
                it0.remove();
                event.onEventAborted(edgeNode0, edgeNode1, this);
                eventQueue.remove(event);
            }
        }
    }


    public void printEvents() {
        System.out.println("Events:");
        eventQueue.stream().sorted().forEach(event -> {
            System.out.println(" - " + event + " in " + event.time);
        });
    }

    public void printNodes() {
        System.out.println("Nodes:");
        for(MovingNode node : movingNodes) {
            System.out.println(" - " + node);
        }
    }


    //
    // Event Factory
    //

    public void tryQueueEdgeEvent(MovingNode n0, MovingNode n1) {
        float eventTime = time + n0.getEdgeCollapseTime();
        logger.debug("Attempting to queue EdgeEvent for nodes {} and {} at time {}", n0.id, n1.id, eventTime);

        // In case of an invalid time (=NaN), this condition will be false.
        if(eventTime <= distance) {
            enqueue(new EdgeEvent(n0, n1, eventTime));
        }
        else{
            logger.trace("EdgeEvent not queued as time exceeds distance");
        }
    }


    public void tryQueueSplitEvent(MovingNode reflexNode, MovingNode op0, MovingNode op1) {
        assert reflexNode.isReflex();

        float eventTime = time + SplitEvent.calcTime(reflexNode, op0, distanceSign);
        logger.debug("Attempting to queue SplitEvent for reflex node {} at time {}", reflexNode.id, eventTime);

        // In case of an invalid time (=NaN), this condition will be false.
        if(eventTime <= distance) {
            SplitEvent splitEvent = new SplitEvent(reflexNode, op0, op1, eventTime);
            enqueue(splitEvent);
        }
        else{
            logger.trace("SplitEvent not queued as time exceeds distance");
        }
    }


    public SplitEvent tryReplaceNearestSplitEvent(MovingNode reflexNode, MovingNode op0, MovingNode op1, SplitEvent nearest) {
        assert reflexNode.isReflex();

        float eventTime = time + SplitEvent.calcTime(reflexNode, op0, distanceSign);
        logger.debug("Attempting to replace nearest SplitEvent for reflex node {} at time {}", reflexNode.id, eventTime);

        if(nearest != null && nearest.time <= eventTime) {
            logger.trace("Existing SplitEvent is nearer, not replacing");
            return nearest;
        }

        // In case of an invalid time (=NaN), this condition will be false.
        if(eventTime <= distance) {
            SplitEvent splitEvent = new SplitEvent(reflexNode, op0, op1, eventTime);
            logger.debug("Created new nearest SplitEvent");
            return splitEvent;
        }

        logger.trace("No new SplitEvent created as time exceeds distance");
        return nearest;
    }


    public void recheckAbortedReflexNodes() {
        logger.debug("Rechecking {} aborted reflex nodes", abortedReflex.size());
        for(MovingNode reflexNode : abortedReflex) {
            if(reflexNode.next != null && reflexNode.isReflex()) {
                logger.trace("Recreating SplitEvents for reflex node {}", reflexNode.id);
                SkeletonEvent.createSplitEvents(reflexNode, this);
            }
        }

        abortedReflex.clear();
    }
}
