// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import com.jme3.math.Vector2f;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MovingNode {
    private static final Logger logger = LoggerFactory.getLogger(MovingNode.class);

    public final String id;
    public SkeletonNode skelNode;

    public MovingNode next = null;
    public MovingNode prev = null;

    final Vector2f edgeDir = new Vector2f();
    private float edgeCollapseTime = 0;

    // Bisector points in move direction which depends on whether we're growing or shrinking. Length determines speed.
    final Vector2f bisector = new Vector2f();
    private boolean reflex = false;

    private final ArrayList<SkeletonEvent> events = new ArrayList<>(); // ArrayList is faster than HashSet. Does its performance scale properly?


    MovingNode(String id) {
        this.id = id;
        logger.debug("Created MovingNode with id: {}", id);
    }


    public float getEdgeCollapseTime() {
        return edgeCollapseTime;
    }

    public boolean isReflex() {
        return reflex;
    }


    public void addEvent(SkeletonEvent event) {
        events.add(event);
        logger.trace("Added event to MovingNode {}: {}", id, event);
    }

    public void removeEvent(SkeletonEvent event) {
        boolean removed = events.remove(event);
        assert removed;
        logger.trace("Removed event from MovingNode {}: {}", id, event);
    }

    public boolean tryRemoveEvent(SkeletonEvent event) {
        boolean removed = events.remove(event);
        if (removed) {
            logger.trace("Successfully removed event from MovingNode {}: {}", id, event);
        }
        return removed;
    }

    public void clearEvents() {
        logger.debug("Clearing all events for MovingNode {}", id);
        events.clear();
    }

    public Iterable<SkeletonEvent> events() {
        return events;
    }


    /**
     * @return True if bisector is valid and polygon is not degenerated at this corner.
     */
    public boolean calcBisector(SkeletonContext ctx) {
        return calcBisector(ctx, false);
    }

    /**
     * @param init True if the calculation is supposed to initialize the bisector.
     * @return True if bisector is valid and polygon is not degenerated at this corner.
     */
    public boolean calcBisector(SkeletonContext ctx, boolean init) {
        logger.debug("Calculating bisector for MovingNode {}, init: {}", id, init);

        if(next.next == this) {
            logger.warn("MovingNode {} is part of a degenerate polygon (only two nodes)", id);
            return false;
        }

        // Calc direction to neighbor nodes. Make sure there's enough distance for stable calculation.
        Vector2f vPrev = prev.skelNode.p.subtract(skelNode.p);
        float vPrevLength = vPrev.length();
        if(vPrevLength < ctx.epsilon) {
            logger.debug("MovingNode {} is too close to its previous node, marked as degenerate", id);
            setDegenerate();
            return false;
        }

        Vector2f vNext = next.skelNode.p.subtract(skelNode.p);
        float vNextLength = vNext.length();
        if(vNextLength < ctx.epsilon) {
            logger.debug("MovingNode {} is too close to its next node, marked as degenerate", id);
            setDegenerate();
            return false;
        }

        // Normalize
        vPrev.divideLocal(vPrevLength);
        vNext.divideLocal(vNextLength);

        // Check if edges point in opposite directions with an angle of 180° between them
        float cos = vPrev.dot(vNext);
        if(cos < ctx.epsilonMinusOne) {
            // Rotate vPrev by 90° counterclockwise
            bisector.x = -vPrev.y * ctx.distanceSign;
            bisector.y = vPrev.x * ctx.distanceSign;
            reflex = false;
            logger.debug("MovingNode {} has 180-degree angle, bisector set perpendicular", id);
        }
        else {
            // This fixes some cases where 90° bisectors (between adjacent edges that point in 180° different directions)
            // don't degenerate as they should. Presumably because these nodes advance too much (without being considered reflex)
            // and then lie on the wrong side of an approaching edge, and/or because of floating point inaccuracy.
            // Therefore we must ensure that vPrev (still) lies left of vNext. This is only a valid check if the node was not reflex
            // and the angle between vPrev and vNext is less than 90°.
            // Another way for catching more degenerates is to increase EPSILON.
            // TODO: THIS CREATES NEW ERRORS WHEN GROWING POLYGONS (see bug22).
            /*boolean reflexBefore = init || reflex;
            if(!reflexBefore && cos > 0 && vPrev.determinant(vNext) > 0) {
                setDegenerate();
                return false;
            }*/

            bisector.set(vPrev).addLocal(vNext).normalizeLocal();
            float sin = vPrev.determinant(bisector);

            // Check if degenerated
            if(Math.abs(sin) < ctx.epsilon) {
                logger.debug("MovingNode {} has degenerate bisector, marked as degenerate", id);
                setDegenerate();
                return false;
            }
            else {
                float speed = ctx.distanceSign / sin;
                bisector.multLocal(speed);
                reflex = (bisector.dot(vPrev) < 0);
                logger.debug("MovingNode {} bisector calculated, reflex: {}", id, reflex);
            }
        }

        return true;
    }


    public void updateEdge() {
        logger.debug("Updating edge for MovingNode {}", id);
        edgeDir.set(next.skelNode.p).subtractLocal(skelNode.p);
        float edgeLength = edgeDir.length();
        edgeDir.divideLocal(edgeLength); // Normalize

        float edgeShrinkSpeed = bisector.dot(edgeDir);
        edgeShrinkSpeed -= next.bisector.dot(edgeDir); // equivalent to: edgeShrinkSpeed += next.bisector.dot(edgeDir.negate());

        if(edgeShrinkSpeed > 0) {
            edgeCollapseTime = edgeLength / edgeShrinkSpeed;
            logger.debug("Edge collapse time for MovingNode {} set to {}", id, edgeCollapseTime);
        }
        else {
            edgeCollapseTime = SkeletonEvent.INVALID_TIME;
            logger.debug("Edge for MovingNode {} is not shrinking, collapse time set to INVALID", id);
        }
    }

    private void setDegenerate() {
        logger.info("Setting MovingNode {} as degenerate", id);
        bisector.zero();
        reflex = false;
    }


    public void leaveSkeletonNode() {
        logger.debug("MovingNode {} leaving its SkeletonNode", id);
        // Leave a SkeletonNode at old place and create new one
        SkeletonNode oldSkelNode = skelNode;
        skelNode = new SkeletonNode();
        skelNode.p.set(oldSkelNode.p);
        oldSkelNode.addEdge(skelNode);
        logger.trace("New SkeletonNode created for MovingNode {}", id);
    }


    @Override
    public String toString() {
        if(reflex)
            return "MovingNode{" + id + " (reflex)}";
        else
            return "MovingNode{" + id + "}";
    }
}
