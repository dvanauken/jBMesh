package ch.alchemists.jbmesh.operator.skeleton;

import ch.alchemists.jbmesh.data.BMeshAttribute;
import ch.alchemists.jbmesh.data.property.Vec3Attribute;
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
        // Create a BMesh and define the footprint
        BMesh bmesh = new BMesh();
        Face footprint = createFootprint(bmesh);

        // Run the Straight Skeleton algorithm
        StraightSkeleton skeleton = new StraightSkeleton(bmesh);
        skeleton.setDistance(-1.0f); // Shrink by 1 unit
        skeleton.apply(footprint);

        // Get the result
        SkeletonVisualization visualization = skeleton.getVisualization();
        BMesh resultMesh = visualization.createSkeletonMappingVis();

        // Output the result as OBJ
        writeObjFile(resultMesh, "straight_skeleton_result.obj");

        System.out.println("Straight Skeleton algorithm completed. Result saved as 'straight_skeleton_result.obj'.");
    }

    private static Face createFootprint(BMesh bmesh) {
        // Define vertices for a simple rectangular footprint
        Vertex v1 = bmesh.createVertex(new Vector3f(0, 0, 0));
        Vertex v2 = bmesh.createVertex(new Vector3f(4, 0, 0));
        Vertex v3 = bmesh.createVertex(new Vector3f(4, 3, 0));
        Vertex v4 = bmesh.createVertex(new Vector3f(0, 3, 0));

        // Create edges
        bmesh.createEdge(v1, v2);
        bmesh.createEdge(v2, v3);
        bmesh.createEdge(v3, v4);
        bmesh.createEdge(v4, v1);

        // Create and return the face
        return bmesh.createFace(v1, v2, v3, v4);
    }

    private static void writeObjFile(BMesh mesh, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            Vec3Attribute<Vertex> positions = Vec3Attribute.get(BMeshAttribute.Position, mesh.vertices());

            // Write vertices
            for (Vertex v : mesh.vertices()) {
                Vector3f pos = positions.get(v);
                writer.write(String.format("v %f %f %f\n", pos.x, pos.y, pos.z));
            }

            // Write edges as lines
            for (Edge e : mesh.edges()) {
                int v1 = e.vertex0.getIndex() + 1; // OBJ indices are 1-based
                int v2 = e.vertex1.getIndex() + 1;
                writer.write(String.format("l %d %d\n", v1, v2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }}