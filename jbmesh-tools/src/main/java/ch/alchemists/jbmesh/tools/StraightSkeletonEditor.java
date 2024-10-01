// Copyright (c) 2020-2021 Rolf Müri
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package ch.alchemists.jbmesh.tools;

import ch.alchemists.jbmesh.operator.skeleton.SkeletonNode;
import ch.alchemists.jbmesh.operator.skeleton.SkeletonVisualization;
import ch.alchemists.jbmesh.operator.skeleton.StraightSkeleton;
import ch.alchemists.jbmesh.structure.BMesh;
import ch.alchemists.jbmesh.structure.Face;
import ch.alchemists.jbmesh.structure.Vertex;
import ch.alchemists.jbmesh.tools.polygoneditor.PolygonEditorState;
import ch.alchemists.jbmesh.util.Profiler;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: See bug7 example
public class StraightSkeletonEditor extends SimpleApplication {

    private StraightSkeleton skeleton;

    private static final Logger logger = LoggerFactory.getLogger(StraightSkeletonEditor.class);

    //private static final String STORAGE_PATH       = "F:/jme/jBMesh/points";
    private static final String STORAGE_PATH = "points";

    //private static final String EXPORT_FILE        = "straight-skeleton.points";
    private static final String EXPORT_FILE = "straight-skeleton.points";

    private static final String ACT_INC_DISTANCE   = "ACT_INC_DISTANCE";
    private static final String ACT_DEC_DISTANCE   = "ACT_DEC_DISTANCE";
    private static final String ACT_MOD_STEP       = "ACT_MOD_STEP";

    private static final String ACT_RESET_DISTANCE = "ACT_RESET_DISTANCE";
    private static final String ACT_MAX_DISTANCE   = "ACT_MAX_DISTANCE";
    private static final String ACT_BENCHMARK      = "ACT_BENCHMARK";
    private static final String ACT_EXPORT         = "ACT_EXPORT";

    private static final String ACT_TOGGLE_SKEL    = "ACT_TOGGLE_SKEL";
    private static final String ACT_TOGGLE_BISECT  = "ACT_TOGGLE_BISECT";

    private static final float SKEL_DISTANCE_STEP  = 0.002f;
    private static final float SKEL_DISTANCE_LEAP  = 0.02f;
    private static final float DEFAULT_DISTANCE    = 0.0f;
    private float skeletonDistance                 = DEFAULT_DISTANCE;
    private boolean modStep = false;

    private boolean showSkel = true;
    private boolean showBisectors = false;

    private final PolygonEditorState polygonEditor;
    private final Node node = new Node("StraightSkeletonEditor");

    private PolygonEditorState.PointDrawType movingNodeType;

    private StraightSkeletonEditor() {
        super((AppState[])null);

        try {
            Files.createDirectories(Paths.get(STORAGE_PATH));
        } catch (IOException e) {
            logger.error("Failed to create directory: " + STORAGE_PATH, e);
        }

        polygonEditor = new PolygonEditorState(pointListener);
        polygonEditor.setStoragePath(STORAGE_PATH);
        stateManager.attach(polygonEditor);

        polygonEditor.importFromDefaultFile();
    }

    @Override
    public void simpleInitApp() {
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        rootNode.attachChild(node);

        inputManager.addMapping(ACT_INC_DISTANCE, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACT_DEC_DISTANCE, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addMapping(ACT_MOD_STEP, new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping(ACT_RESET_DISTANCE, new KeyTrigger(KeyInput.KEY_0));
        inputManager.addMapping(ACT_MAX_DISTANCE, new KeyTrigger(KeyInput.KEY_M));
        inputManager.addMapping(ACT_BENCHMARK, new KeyTrigger(KeyInput.KEY_B));
        inputManager.addMapping(ACT_EXPORT, new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping(ACT_TOGGLE_SKEL, new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping(ACT_TOGGLE_BISECT, new KeyTrigger(KeyInput.KEY_D));

        inputManager.addListener(actionListener, ACT_INC_DISTANCE, ACT_DEC_DISTANCE, ACT_MOD_STEP, ACT_RESET_DISTANCE, ACT_MAX_DISTANCE,
            ACT_BENCHMARK, ACT_EXPORT, ACT_TOGGLE_SKEL, ACT_TOGGLE_BISECT);

        movingNodeType = new PolygonEditorState.PointDrawType(assetManager, ColorRGBA.Black, 0.02f, 0.15f);
        movingNodeType.textColor = new ColorRGBA(0.0f, 0.6f, 0.6f, 1.0f);
        rootNode.attachChild(movingNodeType.container);
    }


    private void updateSkeletonVis() {
        logger.debug("Updating skeleton visualization");
        node.detachAllChildren();
        movingNodeType.container.detachAllChildren();

        BMesh bmesh = new BMesh();
        Face face = polygonEditor.createBMeshFace(bmesh);

        if(face != null) {
            logger.debug("Creating StraightSkeleton with distance: {}", skeletonDistance);
            this.skeleton = new StraightSkeleton(bmesh);
            this.skeleton.setDistance(skeletonDistance);

            // TODO: Add multiple faces & holes
            try(Profiler p = Profiler.start("StraightSkeleton.apply")) {
                skeleton.apply(face);
            }
            logger.debug("Straight skeleton computed");

            SkeletonVisualization skelVis = skeleton.getVisualization();

            logger.debug("Attaching moving nodes visualization");
            node.attachChild( polygonEditor.createLineGeom(skelVis.createMovingNodesVis(), ColorRGBA.Cyan) );

            if(showSkel) {
                logger.debug("Attaching skeleton mapping and degeneracy visualizations");
                node.attachChild(polygonEditor.createLineGeom(skelVis.createSkeletonMappingVis(), ColorRGBA.Yellow));
                node.attachChild(polygonEditor.createLineGeom(skelVis.createSkeletonDegeneracyVis(), ColorRGBA.Brown));
            }

            if(showBisectors) {
                logger.debug("Attaching bisector visualization");
                node.attachChild(polygonEditor.createLineGeom(skelVis.createBisectorVis(), ColorRGBA.Green));
            }
            //node.attachChild( polygonEditor.createLineGeom(skelVis.createMappingVis(), ColorRGBA.Magenta) );

            logger.debug("Creating point visualizations for moving nodes");
            for(SkeletonVisualization.VisNode node : skelVis.getMovingNodes()) {
                polygonEditor.createPointVis(movingNodeType, node.pos, node.name);
            }
        }
        else{
            logger.warn("No valid face created from polygon editor");
        }
        logger.debug("Skeleton visualization update complete");
    }

    private Face createFixedPointsFace(BMesh bmesh) {
        logger.debug("Creating face with fixed points");
        Vertex v1 = bmesh.createVertex(new Vector3f(0, 0, 0));
        Vertex v2 = bmesh.createVertex(new Vector3f(4, 0, 0));
        Vertex v3 = bmesh.createVertex(new Vector3f(4, 3, 0));
        Vertex v4 = bmesh.createVertex(new Vector3f(0, 3, 0));

        bmesh.createEdge(v1, v2);
        bmesh.createEdge(v2, v3);
        bmesh.createEdge(v3, v4);
        bmesh.createEdge(v4, v1);

        return bmesh.createFace(v1, v2, v3, v4);
    }

    private void benchmark() {
        logger.info("Starting benchmark");
        final int runs = 1000;

        BMesh bmesh = new BMesh();
        Face face = polygonEditor.createBMeshFace(bmesh);

        StraightSkeleton skeleton = new StraightSkeleton(bmesh);
        skeletonDistance = Float.NEGATIVE_INFINITY;
        skeleton.setDistance(skeletonDistance);

        for(int i=runs/15; i>=0; --i) {
            skeleton.apply(face);
        }

        try(Profiler p0 = Profiler.start("StraightSkeleton Benchmark")) {
            for(int i=0; i<runs; ++i) {
                try(Profiler p = Profiler.start("StraightSkeleton.apply")) {
                    skeleton.apply(face);
                }

                if((i&2047) == 0)
                    System.gc();
            }
        }

        Profiler.printAndClear();
        updateSkeletonVis();
        logger.info("Benchmark completed");
    }

//    private void exportFile() {
//        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String fileName = "skeleton_export." + timestamp + ".svg";
//        logger.info("Exporting skeleton and polygon to SVG file: {}", fileName);
//
//        try (FileWriter writer = new FileWriter(fileName)) {
//            List<Vector2f> points = polygonEditor.getPoints();
//            logger.info("Number of points in polygon: {}", points.size());
//
//            if (points.size() < 3) {
//                logger.error("Not enough points to create a polygon. Skeleton will not be drawn.");
//                return;
//            }
//
//            Vector2f min = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
//            Vector2f max = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
//
//            // Calculate bounding box
//            for (Vector2f point : points) {
//                min.x = Math.min(min.x, point.x);
//                min.y = Math.min(min.y, point.y);
//                max.x = Math.max(max.x, point.x);
//                max.y = Math.max(max.y, point.y);
//            }
//
//            float width = max.x - min.x;
//            float height = max.y - min.y;
//            float padding = Math.max(width, height) * 0.1f;
//
//            // Start SVG file
//            writer.write(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"1000\" viewBox=\"%f %f %f %f\">\n",
//                    min.x - padding, min.y - padding, width + 2*padding, height + 2*padding));
//
//            // Export polygon
//            writer.write("  <polygon points=\"");
//            for (Vector2f point : points) {
//                writer.write(point.x + "," + point.y + " ");
//            }
//            writer.write("\" fill=\"none\" stroke=\"black\" />\n");
//
//            logger.info("Polygon exported to SVG.");
//
//            // Create BMesh and Face
//            BMesh bmesh = new BMesh();
//            Face face = polygonEditor.createBMeshFace(bmesh);
//
//            if (face == null) {
//                logger.error("Failed to create face from polygon points. Skeleton will not be drawn.");
//                writer.write("</svg>");
//                return;
//            }
//
//            logger.info("Face created successfully. Vertex count: {}", face.getVertices().size());
//
//            // Create and compute skeleton
//            StraightSkeleton skeleton = new StraightSkeleton(bmesh);
//            skeleton.setDistance(skeletonDistance);
//            logger.info("Skeleton distance set to: {}", skeletonDistance);
//
//            try {
//                logger.info("Starting skeleton computation...");
//                skeleton.apply(face);
//                logger.info("Skeleton computation completed successfully.");
//            } catch (Exception e) {
//                logger.error("Error during skeleton computation: ", e);
//                writer.write("</svg>");
//                return;
//            }
//
//            // Export skeleton
//            writer.write("  <g stroke=\"red\">\n");
//
//            List<List<SkeletonNode>> nodeLoops = skeleton.getNodeLoops();
//            logger.info("Number of node loops in skeleton: {}", nodeLoops.size());
//
//            if (nodeLoops.isEmpty()) {
//                logger.warn("No node loops found in skeleton. Skeleton will not be drawn.");
//                writer.write("  </g>\n</svg>");
//                return;
//            }
//
//            int totalEdgesDrawn = 0;
//            for (int loopIndex = 0; loopIndex < nodeLoops.size(); loopIndex++) {
//                List<SkeletonNode> loop = nodeLoops.get(loopIndex);
//                logger.info("Processing loop {} with {} nodes", loopIndex, loop.size());
//
//                for (int i = 0; i < loop.size(); i++) {
//                    SkeletonNode currentNode = loop.get(i);
//                    SkeletonNode nextNode = loop.get((i + 1) % loop.size());
//
//                    Vector3f start = skeleton.getPosition(currentNode);
//                    Vector3f end = skeleton.getPosition(nextNode);
//
//                    // Draw node
//                    writer.write(String.format("    <circle cx=\"%f\" cy=\"%f\" r=\"0.1\" fill=\"red\" />\n", start.x, start.y));
//
//                    // Draw edge
//                    writer.write(String.format("    <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" />\n",
//                            start.x, start.y, end.x, end.y));
//
//                    totalEdgesDrawn++;
//                    logger.debug("Drew edge from ({}, {}) to ({}, {})", start.x, start.y, end.x, end.y);
//                }
//            }
//
//            logger.info("Total skeleton edges drawn: {}", totalEdgesDrawn);
//
//            if (totalEdgesDrawn == 0) {
//                logger.warn("No skeleton edges were drawn. Check skeleton computation or node positions.");
//            }
//
//            writer.write("  </g>\n");
//            writer.write("</svg>");
//
//            logger.info("SVG file exported successfully: {}", fileName);
//        } catch (IOException e) {
//            logger.error("Error exporting to SVG: ", e);
//        }
//    }

//    private void exportFile() {
//        logger.info("Exporting points to file: {}", EXPORT_FILE);
//        polygonEditor.exportPoints(EXPORT_FILE);
//    }

    private final PolygonEditorState.PointListener pointListener = new PolygonEditorState.PointListener() {
        @Override
        public void onPointsReset() {
            logger.debug("Points reset, resetting skeleton distance to default");
            skeletonDistance = DEFAULT_DISTANCE;
        }

        @Override
        public void onPointsUpdated(Map<Integer, ArrayList<Vector2f>> pointMap) {
            logger.debug("Points updated, updating skeleton visualization");
            updateSkeletonVis();
        }
    };


    private final ActionListener actionListener = (String name, boolean isPressed, float tpf) -> {
        if(name.equals(ACT_MOD_STEP)) {
            modStep = isPressed;
            return;
        }

        if(!isPressed)
            return;

        switch(name) {
            case ACT_INC_DISTANCE:
                skeletonDistance += (modStep) ? SKEL_DISTANCE_STEP : SKEL_DISTANCE_LEAP;
                logger.debug("Increased skeleton distance to {}", skeletonDistance);
                updateSkeletonVis();
                break;

            case ACT_DEC_DISTANCE:
                skeletonDistance -= (modStep) ? SKEL_DISTANCE_STEP : SKEL_DISTANCE_LEAP;
                logger.debug("Decreased skeleton distance to {}", skeletonDistance);
                updateSkeletonVis();
                break;

            case ACT_RESET_DISTANCE:
                logger.debug("Reset skeleton distance to 0");
                skeletonDistance = 0;
                updateSkeletonVis();
                break;

            case ACT_MAX_DISTANCE:
                logger.debug("Set skeleton distance to negative infinity");
                skeletonDistance = Float.NEGATIVE_INFINITY;
                updateSkeletonVis();
                break;

            case ACT_BENCHMARK:
                logger.info("Starting benchmark");
                benchmark();
                break;

            case ACT_EXPORT:
                logger.info("Exporting points");
                exportFile();
                break;

            case ACT_TOGGLE_SKEL:
                showSkel ^= true;
                logger.debug("Toggled skeleton visibility: {}", showSkel);
                updateSkeletonVis();
                break;

            case ACT_TOGGLE_BISECT:
                showBisectors ^= true;
                logger.debug("Toggled bisector visibility: {}", showBisectors);
                updateSkeletonVis();
                break;
        }
    };


    public static void main(String[] args) {
        logger.info("Starting StraightSkeletonEditor application");
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        settings.setFrameRate(200);
        settings.setSamples(8);
        settings.setGammaCorrection(true);
        settings.setResizable(true);

        StraightSkeletonEditor app = new StraightSkeletonEditor();
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }



//    private void exportFile() {
//        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String fileName = "skeleton_export." + timestamp + ".svg";
//        logger.info("Exporting straight skeleton to SVG file: {}", fileName);
//
//        try (FileWriter writer = new FileWriter(fileName)) {
//            // Create BMesh and Face
//            BMesh bmesh = new BMesh();
//            Face face = polygonEditor.createBMeshFace(bmesh);
//
//            if (face == null) {
//                logger.error("Failed to create face from polygon points. Skeleton will not be drawn.");
//                return;
//            }
//
//            // Create and compute skeleton
//            StraightSkeleton skeleton = new StraightSkeleton(bmesh);
//            skeleton.setDistance(Float.NEGATIVE_INFINITY); // Ensure full skeleton computation
//
//            try {
//                skeleton.apply(face);
//                logger.info("Skeleton computation completed successfully.");
//            } catch (Exception e) {
//                logger.error("Error during skeleton computation: ", e);
//                return;
//            }
//
//            // Get bounding box for SVG viewBox
//            Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
//            Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
//
//            // Get all skeleton edges
//            List<SkeletonNode> startNodes = skeleton.getStartNodes();
//            Set<SkeletonNode> visitedNodes = new HashSet<>();
//            List<Vector3f[]> edges = new ArrayList<>();
//
//            for (SkeletonNode startNode : startNodes) {
//                collectSkeletonEdges(skeleton, startNode, visitedNodes, edges, min, max);
//            }
//
//            // Calculate SVG dimensions
//            float width = max.x - min.x;
//            float height = max.y - min.y;
//            float padding = Math.max(width, height) * 0.1f;
//
//            // Write SVG header
//            writer.write(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"1000\" viewBox=\"%f %f %f %f\">\n",
//                    min.x - padding, min.y - padding, width + 2*padding, height + 2*padding));
//
//            // Write skeleton edges
//            writer.write("  <g stroke=\"yellow\">\n");
//            for (Vector3f[] edge : edges) {
//                writer.write(String.format("    <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" />\n",
//                        edge[0].x, edge[0].y, edge[1].x, edge[1].y));
//            }
//            writer.write("  </g>\n");
//
//            // Close SVG
//            writer.write("</svg>");
//
//            logger.info("SVG file exported successfully: {}. Total edges: {}", fileName, edges.size());
//        } catch (IOException e) {
//            logger.error("Error exporting to SVG: ", e);
//        }
//    }



    private void exportFile() {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "skeleton_export." + timestamp + ".svg";
        logger.info("Exporting straight skeleton to SVG file: {}", fileName);

        try (FileWriter writer = new FileWriter(fileName)) {
            // ... (previous code for creating BMesh and computing skeleton remains the same)

            // Get bounding box for SVG viewBox
            Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

            // Get all skeleton edges
            List<SkeletonNode> startNodes = skeleton.getStartNodes();
            Set<SkeletonNode> visitedNodes = new HashSet<>();
            List<Vector3f[]> edges = new ArrayList<>();

            for (SkeletonNode startNode : startNodes) {
                collectSkeletonEdges(skeleton, startNode, visitedNodes, edges, min, max);
            }

            // Calculate SVG dimensions
            float width = max.x - min.x;
            float height = max.y - min.y;
            float padding = Math.max(width, height) * 0.1f;

            // Write SVG header with inverted y-axis
            writer.write(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"1000\" viewBox=\"%f %f %f %f\">\n",
                    min.x - padding, -(max.y + padding), width + 2*padding, height + 2*padding));

            // Add a group with y-axis inversion transform
            writer.write("<g transform=\"scale(1,-1)\">\n");

            // Write skeleton edges
            writer.write("  <g stroke=\"yellow\">\n");
            for (Vector3f[] edge : edges) {
                writer.write(String.format("    <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" />\n",
                        edge[0].x, edge[0].y, edge[1].x, edge[1].y));
            }
            writer.write("  </g>\n");

            // Close the transform group
            writer.write("</g>\n");

            // Close SVG
            writer.write("</svg>");

            logger.info("SVG file exported successfully: {}. Total edges: {}", fileName, edges.size());
        } catch (IOException e) {
            logger.error("Error exporting to SVG: ", e);
        }
    }

    private void collectSkeletonEdges(StraightSkeleton skeleton, SkeletonNode node, Set<SkeletonNode> visitedNodes,
                                      List<Vector3f[]> edges, Vector3f min, Vector3f max) {
        if (visitedNodes.contains(node)) {
            return;
        }
        visitedNodes.add(node);

        Vector3f nodePos = skeleton.getPosition(node);
        updateBounds(nodePos, min, max);

        for (Map.Entry<SkeletonNode, SkeletonNode.EdgeType> entry : node.outgoingEdges.entrySet()) {
            SkeletonNode targetNode = entry.getKey();
            Vector3f targetPos = skeleton.getPosition(targetNode);
            updateBounds(targetPos, min, max);

            edges.add(new Vector3f[]{nodePos, targetPos});
            collectSkeletonEdges(skeleton, targetNode, visitedNodes, edges, min, max);
        }
    }

    private void updateBounds(Vector3f point, Vector3f min, Vector3f max) {
        min.x = Math.min(min.x, point.x);
        min.y = Math.min(min.y, point.y);
        max.x = Math.max(max.x, point.x);
        max.y = Math.max(max.y, point.y);
    }


}
