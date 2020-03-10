package ru.ifmo.rain.zagretdinov.implementor;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Class providing directories management utilities for {@link Implementor}
 *
 * @author doreshnikov
 * @version 1.0
 */
public class ImplementorFileUtils {

    /**
     * Temporary directory {@link Path} object where all temporary files created by the {@link Implementor}
     * will be stored
     */
    private Path tempDirectory;

    /**
     * Deleter file visitor static class. Walks directory using {@link Files#walkFileTree(Path, FileVisitor)}
     * and deletes every subdirectory and file in it including starting directory itself
     */
    private static class FileDeleter extends SimpleFileVisitor<Path> {
        /**
         * Default constructor. Created new instance of {@link FileDeleter} class using {@code super()} call
         */
        FileDeleter() {
            super();
        }

        /**
         * File visitor. Visits file and deletes it from file system
         *
         * @param file {@link Path} to file to be deleted
         * @param attrs {@link BasicFileAttributes} file attributes of given {@code file}
         * @return {@link FileVisitResult#CONTINUE} if no error occurs
         * @throws IOException if file deletion fails
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Directory visitor. Visits directory and deleted it from file system
         *
         * @param dir {@link Path} to directory to be deleted
         * @param exc {@link IOException} instance if any error occurs during directory visiting
         * @return {@link FileVisitResult#CONTINUE} if no error occurs
         * @throws IOException if directory deletion fails
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Constructor from {@link Path} object. Creates a new instance of {@link ImplementorFileUtils} with
     * {@link #tempDirectory} created in given location
     *
     * @param root {@link Path} to location where to create {@link #tempDirectory}
     * @throws ImplerException if an error occurs while creating temporary directory in given {@code root}
     */
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

    /**
     * Static method creating all upper directories of given {@link Path} if it has a parent directory
     * @see Path#getParent()
     *
     * @param path {@link Path} pointing to desired location
     * @throws ImplerException if an error occurs while creating directories leading to given {@code path}
     */
    public static void createDirectoriesTo(Path path) throws ImplerException {
        if (path.getParent() != null) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new ImplerException(String.format("Unable to create directories: %s", e.getMessage()));
            }
        }
    }

    /**
     * Getter for {@link #tempDirectory}
     *
     * @return temporary directory {@link Path} stored in this instance of {@link ImplementorFileUtils}
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Recursively deletes {@link #tempDirectory} using {@link FileDeleter}
     * @see Files#walkFileTree(Path, FileVisitor)
     *
     * @throws ImplerException if an error occurs during temporary directory deletion
     */
    public void cleanTempDirectory() throws ImplerException {
        try {
            Files.walkFileTree(tempDirectory, new FileDeleter());
        } catch (IOException e) {
            throw new ImplerException("Can not delete temporary directory");
        }
    }

}