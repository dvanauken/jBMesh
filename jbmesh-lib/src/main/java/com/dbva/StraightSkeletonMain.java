package com.dbva;

import ch.alchemists.jbmesh.data.BMeshAttribute;
import ch.alchemists.jbmesh.data.property.Vec3Attribute;
import ch.alchemists.jbmesh.operator.skeleton.SkeletonVisualization;
import ch.alchemists.jbmesh.operator.skeleton.StraightSkeleton;
import ch.alchemists.jbmesh.structure.BMesh;
import ch.alchemists.jbmesh.structure.Edge;
import ch.alchemists.jbmesh.structure.Face;
import ch.alchemists.jbmesh.structure.Vertex;
import com.jme3.math.Vector3f;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class StraightSkeletonMain {
  public static void main(String[] args) {
    // Step 1: Create a BMesh and define the footprint
    BMesh bmesh = new BMesh();
    Face footprint = createFootprint(bmesh);

    // Step 2: Run the Straight Skeleton algorithm on the footprint
    StraightSkeleton skeleton = new StraightSkeleton(bmesh);
    skeleton.setDistance(-Float.MAX_VALUE);  // Compute full inward skeleton
    skeleton.apply(footprint);

    // Step 3: Get the result and convert it to a 2D structure
    SkeletonVisualization visualization = skeleton.getVisualization();
    BMesh resultMesh = visualization.createSkeletonMappingVis();

    // Step 4: Output the result as an SVG file
    writeSvgFile(bmesh, resultMesh, "straight_skeleton_result.svg");

    System.out.println("Straight Skeleton algorithm completed. Result saved as 'straight_skeleton_result.svg'.");
  }

  /**
   * This method creates a simple rectangular footprint for the building.
   * The vertices are placed in 2D on the X-Y plane.
   * @param bmesh The BMesh object to store vertices, edges, and faces.
   * @return The face representing the rectangular footprint.
   */
  private static Face createFootprint(BMesh bmesh) {
    // Define vertices for a simple rectangular footprint
    Vertex v1 = bmesh.createVertex(new Vector3f(0, 0, 0));
    Vertex v2 = bmesh.createVertex(new Vector3f(4, 0, 0));
    Vertex v3 = bmesh.createVertex(new Vector3f(4, 3, 0));
    Vertex v4 = bmesh.createVertex(new Vector3f(0, 3, 0));

    // Create edges between the vertices
    bmesh.createEdge(v1, v2);
    bmesh.createEdge(v2, v3);
    bmesh.createEdge(v3, v4);
    bmesh.createEdge(v4, v1);

    // Create and return the face using the vertices
    return bmesh.createFace(v1, v2, v3, v4);
  }

  /**
   * This method writes the footprint and the skeleton as an SVG file.
   * The building footprint is represented as a polygon, and the skeleton as a line.
   * @param footprintMesh The BMesh object containing the vertices and edges of the footprint.
   * @param skeletonMesh The BMesh object containing the vertices and edges of the skeleton.
   * @param filename The name of the SVG file to write to.
   */
  private static void writeSvgFile(BMesh footprintMesh, BMesh skeletonMesh, String filename) {
    try (FileWriter writer = new FileWriter(filename)) {
      // Write the SVG header
      writer.write("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"1000\" height=\"1000\">\n");

      Vec3Attribute<Vertex> positions = Vec3Attribute.get(BMeshAttribute.Position, footprintMesh.vertices());

      // Step 1: Draw the building footprint as a polygon
      writer.write("<polygon points=\"");
      for (Vertex v : footprintMesh.vertices()) {
        Vector3f pos = positions.get(v);
        writer.write(String.format("%f,%f ", pos.x * 100, pos.y * 100));  // Scale up for visibility
      }
      writer.write("\" style=\"fill:none;stroke:black;stroke-width:2\" />\n");

      // Step 2: Draw the skeleton as lines (including ridge lines)
      Vec3Attribute<Vertex> skeletonPositions = Vec3Attribute.get(BMeshAttribute.Position, skeletonMesh.vertices());
      for (Edge e : skeletonMesh.edges()) {
        Vector3f v1 = skeletonPositions.get(e.vertex0);
        Vector3f v2 = skeletonPositions.get(e.vertex1);
        writer.write(String.format("<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"red\" stroke-width=\"1\" />\n",
            v1.x * 100, v1.y * 100, v2.x * 100, v2.y * 100));  // Scale up for visibility
      }

      // Step 3: Add the central ridge line (manual addition based on footprint geometry)
      // Find the midpoint of the edges on the long sides of the rectangle and connect them
      Vector3f midLeft = new Vector3f((0 + 0) / 2, (0 + 3) / 2, 0);  // Midpoint of the left edge (v1-v4)
      Vector3f midRight = new Vector3f((4 + 4) / 2, (0 + 3) / 2, 0);  // Midpoint of the right edge (v2-v3)

      writer.write(String.format("<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"green\" stroke-width=\"2\" />\n",
          midLeft.x * 100, midLeft.y * 100, midRight.x * 100, midRight.y * 100));  // Scale up for visibility

      // Write the closing tag for the SVG
      writer.write("</svg>");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
