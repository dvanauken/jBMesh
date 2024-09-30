package com.dbva;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class SkeletonCodeExtractor {
  private static final String OUTPUT_FILE = "skeleton_code_summary.txt";

  public static void main(String[] args) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {
      Files.walk(Paths.get("."))
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> processFile(path, writer));

      System.out.println("Code extraction completed. Output written to " + OUTPUT_FILE);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void processFile(Path filePath, BufferedWriter writer) {
    try {
      String fileName = filePath.getFileName().toString();
      String content = Files.lines(filePath)
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .collect(Collectors.joining(" "));

      writer.write("<<" + fileName + ">>");
      writer.newLine();
      writer.write("----------------------");
      writer.newLine();
      writer.write(content);
      writer.newLine();
      writer.newLine();
    } catch (IOException e) {
      System.err.println("Error processing file: " + filePath);
      e.printStackTrace();
    }
  }
}