package com.dbva;

import ch.alchemists.jbmesh.structure.BMesh;
import ch.alchemists.jbmesh.structure.Face;
import ch.alchemists.jbmesh.structure.Vertex;
import ch.alchemists.jbmesh.operator.skeleton.StraightSkeleton;
import ch.alchemists.jbmesh.operator.skeleton.SkeletonNode;
import ch.alchemists.jbmesh.conversion.BMeshJmeExport;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import java.util.List;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class HipRoofGenerator {
    public static void main(String[] args) {
        // Create a new BMesh to represent our building footprint
        BMesh bmesh = new BMesh();

        // Define a simple building footprint (a rectangle with an extension)
        List<Vector3f> polygonVertices = new ArrayList<>();
        polygonVertices.add(new Vector3f(0, 0, 0));
        polygonVertices.add(new Vector3f(10, 0, 0));
        polygonVertices.add(new Vector3f(10, 0, 8));
        polygonVertices.add(new Vector3f(7, 0, 8));
        polygonVertices.add(new Vector3f(7, 0, 10));
        polygonVertices.add(new Vector3f(0, 0, 10));

        // Create the footprint face
        Face footprint = createFootprint(bmesh, polygonVertices);

        // Generate the hip roof using straight skeleton
        generateHipRoof(bmesh, footprint);

        // Export the result as a JMonkeyEngine mesh
        Mesh resultMesh = BMeshJmeExport.exportTriangles(bmesh);

        // Save the result
        saveToObjFile(resultMesh, "hip_roof.obj");

        System.out.println("Hip roof generated and saved to hip_roof.obj");
    }

    private static Face createFootprint(BMesh bmesh, List<Vector3f> polygonVertices) {
        List<Vertex> faceVertices = new ArrayList<>();
        for (Vector3f v : polygonVertices) {
            Vertex vertex = bmesh.createVertex(v.x, v.y, v.z);
            faceVertices.add(vertex);
        }
        return bmesh.createFace(faceVertices);
    }

    private static void generateHipRoof(BMesh bmesh, Face footprint) {
        StraightSkeleton skeleton = new StraightSkeleton(bmesh);
        skeleton.setDistance(3.0f); // Set the roof height
        skeleton.apply(footprint);

        // Retrieve the skeleton nodes and create roof vertices
        List<SkeletonNode> roofNodes = skeleton.getEndNodes();
        for (SkeletonNode node : roofNodes) {
            Vector3f roofPoint = skeleton.getPosition(node);
            bmesh.createVertex(roofPoint.x, roofPoint.y, roofPoint.z);
        }

        // Note: Creating actual roof faces is omitted for simplicity
    }

    private static void saveToObjFile(Mesh mesh, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // Write vertices
            FloatBuffer vertexBuffer = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            for (int i = 0; i < vertexBuffer.limit(); i += 3) {
                writer.write(String.format("v %f %f %f\n",
                        vertexBuffer.get(i), vertexBuffer.get(i+1), vertexBuffer.get(i+2)));
            }

            // Write faces
            ShortBuffer indexBuffer = mesh.getShortBuffer(VertexBuffer.Type.Index);
            for (int i = 0; i < indexBuffer.limit(); i += 3) {
                writer.write(String.format("f %d %d %d\n",
                        indexBuffer.get(i)+1, indexBuffer.get(i+1)+1, indexBuffer.get(i+2)+1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}