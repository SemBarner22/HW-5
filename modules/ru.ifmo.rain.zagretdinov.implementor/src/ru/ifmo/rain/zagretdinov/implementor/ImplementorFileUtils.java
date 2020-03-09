package ru.ifmo.rain.zagretdinov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class ImplementorFileUtils {

    private Path tempDirectory;

    private class FileDeleter extends SimpleFileVisitor<Path> {

        FileDeleter() {
            super();
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    ImplementorFileUtils(Path root) throws ImplerException {
        if (root == null) {
            throw new ImplerException("Invalid directory provided");
        }
        try {
            tempDirectory = Files.createTempDirectory(root.toAbsolutePath(), "tempdir");
        } catch (IOException e) {
            throw new ImplerException(String.format("Unable to create temporary directory: %s", e.getMessage()));
        }
    }

    public static void createDirectoriesTo(Path path) throws ImplerException {
        if (path.getParent() != null) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new ImplerException(String.format("Unable to create directories: %s", e.getMessage()));
            }
        }
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    public void cleanTempDirectory() throws ImplerException {
        try {
            Files.walkFileTree(tempDirectory, new FileDeleter());
        } catch (IOException e) {
            throw new ImplerException("Can not delete temporary directory");
        }
    }
}