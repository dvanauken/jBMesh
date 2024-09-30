// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.operator.skeleton;

import com.jme3.math.Vector2f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkeletonNode {

    private static final Logger logger = LoggerFactory.getLogger(SkeletonNode.class);

    public static enum EdgeType {
        Mapping, Degeneracy
    }

    final Vector2f p = new Vector2f();
    final Map<SkeletonNode, EdgeType> outgoingEdges = new HashMap<>(2);
    final Map<SkeletonNode, EdgeType> incomingEdges = new HashMap<>(2);

    private boolean reflex = false;


    SkeletonNode() {
        logger.trace("Created new SkeletonNode");
    }


    /**
     * Marks this SkeletonNode as connected to a reflex vertex.
     */
    void setReflex() {
        reflex = true;
        logger.debug("SkeletonNode marked as reflex");
    }

    /**
     * @return Whether this SkeletonNode is connected to a reflex vertex.
     */
    public boolean isReflex() {
        return reflex;
    }


    void addEdge(SkeletonNode target) {
        addEdge(target, EdgeType.Mapping);
    }


    // TODO: This should be a different type of edge? It doesn't map an initial vertex to another SkeletonNode. The mapping should stay.
    //       Edges from degeneration don't continue mapping of initial vertices. Degeneration = stop moving inwards, only connect inner skeleton nodes.
    void addDegenerationEdge(SkeletonNode target) {
        logger.debug("Adding degeneration edge to SkeletonNode");
        addEdge(target, EdgeType.Degeneracy);
    }


    private void addEdge(SkeletonNode target, EdgeType type) {
        outgoingEdges.put(target, type);
        target.incomingEdges.put(this, type);
        logger.trace("Added {} edge to SkeletonNode", type);
    }


    void remapIncoming(SkeletonNode newTarget) {
        assert outgoingEdges.isEmpty();
        logger.debug("Remapping incoming edges to new target SkeletonNode");

        for(Map.Entry<SkeletonNode, EdgeType> entry : incomingEdges.entrySet()) {
            entry.getKey().outgoingEdges.remove(this);
            entry.getKey().addEdge(newTarget, entry.getValue());
            logger.trace("Remapped edge from source to new target");
        }

        incomingEdges.clear();
        logger.debug("Completed remapping of incoming edges");
    }


    public void followGraphInward(List<SkeletonNode> storeTargets) {
        logger.debug("Following graph inward");
        boolean leaf = true;

        for(Map.Entry<SkeletonNode, SkeletonNode.EdgeType> entry : outgoingEdges.entrySet()) {
            if(entry.getValue() == SkeletonNode.EdgeType.Mapping) {
                logger.trace("Following mapping edge");
                entry.getKey().followGraphInward(storeTargets);
                leaf = false;
            }
            /*else {
                storeTargets.add(entry.getKey());
            }*/
        }

        if(leaf) {
            storeTargets.add(this);
            logger.debug("Reached leaf node, added to targets");
        }
    }
}
