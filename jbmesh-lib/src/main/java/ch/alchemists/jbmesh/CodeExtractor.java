package ch.alchemists.jbmesh;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class CodeExtractor {
  private static final String OUTPUT_DIR = "d:\\code\\intellij\\jBMesh";
  private static final String OUTPUT_FILE_PREFIX = "code.";
  private static final String OUTPUT_FILE_EXTENSION = ".txt";
  private static final String COPYRIGHT_START = "// Copyright (c) 2020-2021 Rolf Müri";
  private static final String COPYRIGHT_END = "// file, You can obtain one at https://mozilla.org/MPL/2.0/.";

  public static void main(String[] args) {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String outputFileName = OUTPUT_FILE_PREFIX + timestamp + OUTPUT_FILE_EXTENSION;
    Path outputDir = Paths.get(OUTPUT_DIR);
    Path outputPath = outputDir.resolve(outputFileName);

    try {
      Files.createDirectories(outputDir);

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toString()))) {
        Path startPath = Paths.get(".").toAbsolutePath().normalize();
        Files.walk(startPath)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.getFileName().toString().equals("CodeExtractor.java"))
            .forEach(path -> processFile(path, writer));
        System.out.println("Code extraction completed. Output written to " + outputPath);
      }
    } catch (IOException e) {
      System.err.println("Error occurred while processing files or writing output:");
      e.printStackTrace();
    }
  }

  private static void processFile(Path filePath, BufferedWriter writer) {
    try {
      String fileName = filePath.getFileName().toString();
      String content = Files.lines(filePath)
          .dropWhile(line -> line.trim().startsWith("//") && !line.contains(COPYRIGHT_END))
          .skip(1) // Skip the line containing COPYRIGHT_END
          .filter(line -> !line.trim().isEmpty()) // Skip empty lines
          .map(String::trim) // Remove leading/trailing whitespace
          .collect(Collectors.joining(" ")); // Join lines with a single space

      writer.write(fileName + ": " + content);
      writer.newLine(); // Single newline between files
    } catch (IOException e) {
      System.err.println("Error processing file: " + filePath);
      e.printStackTrace();
    }
  }
}