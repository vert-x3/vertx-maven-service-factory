package io.vertx.maven.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FileUtils {

  public static void delete(File root) {
    if (!root.exists()) {
      return;
    }
    Path directory = root.toPath();
    try {
      java.nio.file.Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          java.nio.file.Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          java.nio.file.Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }

      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
