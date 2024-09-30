// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EdgeEvent extends SkeletonEvent {
    private static final Logger logger = LoggerFactory.getLogger(EdgeEvent.class);
    private final MovingNode n0; // Edge start
    private final MovingNode n1; // Edge end


    EdgeEvent(MovingNode n0, MovingNode n1, float time) {
        super(time);
        this.n0 = n0;
        this.n1 = n1;

        assert n0 != n1;
        assert n0.next == n1;
        logger.debug("Created EdgeEvent for edge {}-{} at time {}", n0.id, n1.id, time);
    }


    @Override
    public void onEventQueued() {
        logger.trace("EdgeEvent for edge {}-{} queued", n0.id, n1.id);
        n0.addEvent(this);
        n1.addEvent(this);
    }

    @Override
    public void onEventAborted(MovingNode adjacentNode, SkeletonContext ctx) {
        logger.debug("EdgeEvent for edge {}-{} aborted due to adjacent node {}", n0.id, n1.id, adjacentNode.id);
        // Remove other
        if(adjacentNode == n0)
            n1.removeEvent(this);
        else
            n0.removeEvent(this);
    }

    @Override
    public void onEventAborted(MovingNode edgeNode0, MovingNode edgeNode1, SkeletonContext ctx) {
        logger.trace("EdgeEvent for edge {}-{} aborted due to edge {}-{}", n0.id, n1.id, edgeNode0.id, edgeNode1.id);
    }


    @Override
    public void handle(SkeletonContext ctx) {
        logger.info("Handling EdgeEvent for edge {}-{}", n0.id, n1.id);
        assert n0.next == n1;

        // Merge nodes: keep n0, remove n1
        MovingNode next = n1.next;
        n0.next = next;
        next.prev = n0;

        if(n0.isReflex() || n1.isReflex()) {
            n0.skelNode.setReflex();
            logger.debug("Merged node marked as reflex");
        }

        n1.skelNode.remapIncoming(n0.skelNode);
        logger.debug("Remapped incoming edges from n1 to n0");

        ctx.removeMovingNode(n1);
        logger.debug("Removed MovingNode n1 ({})", n1.id);

        handle(n0, ctx);
        logger.debug("Finished handling EdgeEvent");
    }


    @Override
    public String toString() {
        return "EdgeEvent{" + n0.id + "-" + n1.id + "}";
    }
}
