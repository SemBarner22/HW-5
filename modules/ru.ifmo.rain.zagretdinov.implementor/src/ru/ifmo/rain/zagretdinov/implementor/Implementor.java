package ru.ifmo.rain.zagretdinov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.security.CodeSource;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * Class implements {@link JarImpler}. Provides methods to implement <code>.java/.jar</code> files
 * from given class or interface.
 *
 * @author sem
 * @version 1.0
 */
public class Implementor implements Impler, JarImpler {

    /**
     * Tabulation for generated class.
     */
    private final String TAB = "\t";

    /**
     * Space for generated class.
     */
    private final String SPACE = " ";

    /**
     * Line-separator for generated class.
     */
    private final String LINE_SEP = System.lineSeparator();

    /**
     * Separator for tokens in generated class.
     */
    private final String COLLECTION_SEPARATOR = ", ";

    /**
     * Separator for operations in generated class.
     */
    private final String OPER_SEP = ";";

    /**
     * Opening brace in generated class.
     */
    private final String BLOCK_BEGIN = "{";

    /**
     * Closing brace in generated class.
     */
    private final String BLOCK_END = "}";

    /**
     * Opening bracket in generated class.
     */
    private final String BRACKET_OPEN = "(";

    /**
     * Closing bracket in generated class.
     */
    private final String BRACKET_END = ")";

    /**
     * Function used to determine whether generate <code>.jar</code> or <code>.java</code> file.
     * Continues working with {@link Implementor} in two different scenarios:
     * <ul>
     * <li> 2 arguments <code>className outputPath</code>: creates <code>.java</code> file executing
     * method {@link #implement(Class, Path)} provided by interface {@link Impler} </li>
     * <li> 3 arguments <code>-jar className outputPath</code>: creates <code>.jar</code> file executing
     *      * method {@link #implementJar(Class, Path)} provided by interface {@link JarImpler} </li>
     * </ul>
     * Arguments should not be null. If input is incorrect or an error happens during executing
     * message is printed and execution is aborted.
     * @param args console line arguments: <code>[-jar] className outputPath</code>
     */
    public static void main(String[] args) {
        if (args == null || args.length < 2 || args.length > 3) {
            System.err.println("Invalid arguments number, expected [-jar] <class.name> <output.path>");
        } else {
            for (String arg : args) {
                if (arg == null) {
                    System.err.println("All arguments should be not null");
                    return;
                }
            }
            try {
                if (args.length == 2) {
                    new Implementor().implement(Class.forName(args[0]), Path.of(args[1]));
                } else if (args[0].equals("-jar") || args[0].equals("--jar")) {
                    new Implementor().implementJar(Class.forName(args[1]), Path.of(args[2]));
                } else {
                    System.err.println("expected -jar or --jar");
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Invalid class name given: " + e.getMessage());
            } catch (InvalidPathException e) {
                System.err.println("Invalid path given: " + e.getMessage());
            } catch (ImplerException e) {
                System.err.println("Error while creating " +
                        ((args.length == 2) ? "java" : "jar") + " file " + e.getMessage());
            }
        }
    }

    /**
     * Joins mapped elements with given delimiter. Maps each element to {@link String}
     * and concatenates them with delimiter.
     *
     * @param delimiter {@link String} separating given elements.
     * @param elements array of values to be mapped to {@link String}s and joined together.
     * @param function transforming to {@link String} function.
     * @param <T> type of elements given
     * @return a {@link String} containing concatenated with {@code delimiter} mapped {@code elements}.
     */
    private <T> String elementsToString(String delimiter, T[] elements, Function<T, String> function) {
        return Arrays.stream(elements).map(function).collect(Collectors.joining(delimiter));
    }

    /**
     *  Joins mapped elements. Maps each element to {@link String}
     *  and concatenates them with delimiter {@link #COLLECTION_SEPARATOR}.
     * @param items array of values to be mapped to {@link String}s and joined together.
     * @param transform transforming to {@link String} function.
     * @param <T> type of elements given
     * @return a {@link String} containing concatenated with
     * {@link #COLLECTION_SEPARATOR} mapped {@code elements}.
     */
    private <T> String elementsInCollectionToString(T[] items, Function<T, String> transform) {
        return elementsToString(COLLECTION_SEPARATOR, items, transform);
    }

    /**
     * Joins elements with {@link #SPACE} with braces around.
     * @param parts array of values to be joined together.
     * @return a string containing {@link #BRACKET_OPEN}, {@link #LINE_SEP}, elements joined together
     * with {@link #SPACE}, {@link #LINE_SEP}, {@link #BRACKET_END}.
     */
    private String elementsInBrackets(String... parts) {
        return elementsLineSeparated(BRACKET_OPEN, String.join(SPACE, parts), BRACKET_END);
    }

    /**
     * Joins elements with {@link #SPACE}.
     * @param parts array of values to be joined together.
     * @return a string containing elements joined together
     *      * with {@link #SPACE}.
     */
    private String elementsSpaced(String... parts) {
        return String.join(SPACE, parts);
    }

    /**
     * Joins elements with {@link #LINE_SEP}.
     * @param blocks array of values to be joined together.
     * @return a string containing elements joined together
     * with {@link #LINE_SEP}, string ends with additional {@link #LINE_SEP}.
     */
    private String elementsLineSeparated(String... blocks) {
        return String.join(LINE_SEP, blocks) + LINE_SEP;
    }

    /**
     * Joins elements with {@link #LINE_SEP}.
     * @param cnt amount of {@link #TAB} to be written.
     * @return a string containing {@link #TAB} given {@link Integer} times.
     */
    private String Tabs(int cnt) {
        return TAB.repeat(Math.max(0, cnt));
    }

    /**
     * Writes a package info in generated class.
     * @param items {@link String} package info.
     * @return <code>""</code> if there is no package otherwise <code>package</code> with package name
     * ending with {@link #OPER_SEP}
     */
    private String emptyOrPrefix(String items) {
        if (!"".equals(items)) {
            return elementsSpaced("package", items) + OPER_SEP;
        }
        return "";
    }

    /**
     * Changes every <code>'.'</code> symbol to {@code File.separatorChar}
     * @param token {@link Class} which name of a package is to be changed.
     * @return {@link String} which has every '.' symbol replaced with {@code File.separatorChar}
     */
    private String getFilePath(Class<?> token) {
        return token.getPackageName().replace('.', File.separatorChar);
    }

    /** Name for a generated class. methods gets a name for a given class or interface
     * and appends it with "Impl" suffix.
     * @param token given classname
     * @return {@link String} for a generated class extending or implemented given one.
     */
    private static String getClassName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Function used to create a compiled <code>.jar</code> file implementing methods in given class or interface.
     *  Uses {@link #compileClass(Class, Path)} to compile a file, {@link #implement(Class, Path)}
     *  to implement {@code token} class in location specified by {@code jarFile}.
     * @param token {@link Class} to create implementation for.
     * @param jarFile target future <tt>.jar</tt> file.
     * @throws ImplerException if {@link Class} or {@link Path} is null.
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        ImplementorFileUtils.createDirectoriesTo(jarFile.normalize());
        ImplementorFileUtils utils = new ImplementorFileUtils(jarFile.toAbsolutePath().getParent());
        try {
            implement(token, utils.getTempDirectory());
            compileClass(token, utils.getTempDirectory());
            buildJar(jarFile, utils.getTempDirectory(), token);
        } finally {
            utils.cleanTempDirectory();
        }
    }

    /**
     * Gets implementation path of a given file. Changes splitters in path with
     * <code>File.separator</code>
     * @param token {@link Class} to get path of its implementation.
     * @return {@link String} of implementation path where <code>"\\."</code> splitter
     * is replaced by <code>File.separator</code>
     */
    private static String getImplementationPath(Class<?> token) {
        return String.join(File.separator, token.getPackageName().split("\\.")) +
                File.separator +
                token.getSimpleName();
    }

    /**
     * Method for compiling created <code>.jar</code> file. Finds a {@link JavaCompiler},
     * runs it with a command to compile generated class.
     * @param token type token to create implementation for.
     * @param tmpDir {@link Path} for a temporary directory used for creating a compiled
     * <code>.jar</code> class.
     * @throws ImplerException if {@link JavaCompiler} could not be find or it could not be run.
     */
    private void compileClass(Class<?> token, Path tmpDir) throws ImplerException {
        Path superPath;
        try {
            CodeSource superCodeSource = token.getProtectionDomain().getCodeSource();
            superPath = Path.of((superCodeSource == null) ? "" : superCodeSource.getLocation().getPath());
        } catch (InvalidPathException e) {
            throw new ImplerException("Failed to generate valid classpath", e);
        }
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new ImplerException("No compiler provided");
        }
        String[] compilerArgs = {
                "-cp",
                tmpDir.toString() + File.pathSeparator + superPath.toString(),
                Path.of(tmpDir.toString(), getImplementationPath(token) + "Impl.java").toString(),
        };
        int returnCode = javaCompiler.run(null, null, null, compilerArgs);
        if (returnCode != 0) {
            throw new ImplerException("Implementation compilation returned non-zero code " + returnCode);
        }
    }

    /**
     * Creates a <code>.jar</code> file containing implementation for a given class or interface.
     * Creates a {@link Manifest} for an <code>.jar</code> file.
     * @param jarFile target <tt>.jar</tt> file.
     * @param tempDirectory {@link Path} for a temporary directory used for building a compiled
     * <code>.jar</code> class.
     * @param token {@link Class} token to create implementation for.
     * @throws ImplerException if {@link JarOutputStream} could not be created.
     */
    private void buildJar(Path jarFile, Path tempDirectory, Class<?> token) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream stream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            String pathSuffix = token.getName().replace('.', '/') + "Impl.class";
            stream.putNextEntry(new ZipEntry(pathSuffix));
            Files.copy(Paths.get(tempDirectory.toString(), pathSuffix), stream);
        } catch (IOException e) {
            throw new ImplerException("Could not write to " + jarFile);
        }
    }

    /**
     * Function used to create a <code>.java</code> file implementing methods in given class or interface.
     * to implement {@code token} class in location specified by {@code root}.
     * @param token type token to create implementation for.
     * @param root target future <tt>.jar</tt> file.
     * @throws ImplerException if {@link Path} is incorrect, could not create parent directories for a path
     * or {@link BufferedWriter} could not be created.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        Path place = null;
        try {
            place = Path.of(root.toString(), getFilePath(token),
                    getClassName(token) + ".java");
            Files.createDirectories(place.getParent());
        } catch (InvalidPathException e) {
            throw new ImplerException("Wrong path");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (token.isPrimitive() || token.isArray() ||
                Modifier.isFinal(token.getModifiers()) || token == Enum.class) {
            throw new ImplerException("Unsupported token given");
        }
        String extendsOrImplements = token.isInterface() ? "implements" : "extends";
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(place)) {
            bufferedWriter.write(elementsLineSeparated(emptyOrPrefix(token.getPackageName())
                    , elementsSpaced(getClassModifiers(token),
                            "class", getClassName(token), extendsOrImplements,
                            token.getCanonicalName(), BLOCK_BEGIN)));
            allWork(token, bufferedWriter);
            bufferedWriter.write(BLOCK_END);
        } catch (IOException e) {
            throw new ImplerException("Error with writing class code");
        }
    }

    /**
     *  Returns default value for a given class.
     * @param clazz default value for which should be returned
     * @return {@link String} containing default value for a given {@link Class}
     */
    private String getDefaultValue(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return "null";
        } else if (clazz.equals(void.class)) {
            return "";
        } else if (clazz.equals(boolean.class)) {
            return "false";
        } else {
            return "0";
        }
    }

    /**
     * Writes methods which override parent's ones.
     * @param methodsHashed {@link Set} to check whether this method was worked with before
     * @param methods of given class
     * @param modifier1 modifier for a {@link Class}
     * @param modifier2 modifier for a {@link Class}
     * @param bufferedWriter for writing methods body in generated class
     */
    private void methodWalker(Set<Integer> methodsHashed, Method[] methods, int modifier1, int modifier2,
                              BufferedWriter bufferedWriter) {
        Arrays.stream(methods).forEach(method -> {
            StringBuilder hashing = new StringBuilder();
            hashing.append(method.getReturnType().toString());
            for (Class<?> m : method.getParameterTypes()) {
                hashing.append(m.getCanonicalName());
            }
            int hash = hashing.hashCode();
            if (methodsHashed.add(hash)) {
                if ((method.getModifiers() & modifier1) != 0
                        && (method.getModifiers() & modifier2) != 0) {
                    methodWalk(method, bufferedWriter);
                }
            }
        });
    }

    /**
     * Class for creating constructors parameters. It contains one {@link Integer} field which can be
     * incremented.
     */
    private class Indices {
        int index = 1;

        /**Increments {@code index} value by one and its previous value.
         * @return {@code index} value.
         */
        Integer add() {
            return index++;
        }
    }

    /**
     * Writes to generated file implementations of methods and constructors.
     * @param token {@link Class} implementation of which is needed.
     * @param bufferedWriter for writing methods body in generated class
     * @throws ImplerException if there are no non-private constructors for a given class.
     */
    private void allWork(Class<?> token, BufferedWriter bufferedWriter) throws ImplerException {
        if (!token.isInterface()) {
            List<Constructor<?>> constructors = Arrays.stream(token.getDeclaredConstructors())
                    .filter(c -> !Modifier.isPrivate(c.getModifiers()))
                    .collect(Collectors.toList());
            if (constructors.isEmpty()) {
                throw new ImplerException("Class with no non-private constructors can not be extended");
            }
            Arrays.stream(token.getDeclaredConstructors()).forEach(constructor -> {
                try {
                    bufferedWriter.write(getMethodBody(constructor, token));
                } catch (IOException e) {
                    System.out.println("Error happened while writing constructors code");
                }
            });
        }

        Set<Integer> methodsHashed = new HashSet<>();
        methodWalker(methodsHashed, token.getMethods(), ~Modifier.STATIC, Modifier.ABSTRACT, bufferedWriter);
        Class cur = token;
        while (cur != null) {
            if (Modifier.isPrivate(cur.getModifiers())) {
                throw new ImplerException("Private class in hierarchy");
            }
            methodWalker(methodsHashed, cur.getDeclaredMethods(),
                    Modifier.ABSTRACT, Modifier.PROTECTED, bufferedWriter);
            cur = cur.getSuperclass();
        }
    }

    /**
     *  Writes implementation for a given method. Uses {@link #getMethodBody(Method)} to write implementation,
     * if {@link IOException} happens, message is printed.
     * @param method for which implementation is written
     * @param bufferedWriter used to write implementation to <code>.jar</code> or <code>.java</code> file.
     * @return {@link String} containing implementation for a {@link Method} in generated class.
     */
    private void methodWalk(Method method, BufferedWriter bufferedWriter) {
        try {
            bufferedWriter.write(getMethodBody(method));
        } catch (IOException e) {
            System.out.println("Error happened while writing methods code");
        }
    }

    /**
     * Writes implementation for a given method.
     * @param method for which implementation is written
     * @return {@link String} containing implementation for a {@link Method} in generated class.
     */
    private String getMethodBody(Method method) {
        return elementsSpaced(elementsLineSeparated(Tabs(1),
                Tabs(1) + getMethodModifiers(method),
                method.getReturnType().getCanonicalName(), method.getName(),
                elementsInBrackets(getParameters(method.getParameterTypes())),
                getThrowable(method.getExceptionTypes()),
                BLOCK_BEGIN, Tabs(2) + "return",
                getDefaultValue(method.getReturnType()) +
                        OPER_SEP, Tabs(1) + BLOCK_END));
    }

    /**
     * Writes implementation for a given constructor.
     * @param constructor for which implementation is written
     * @return {@link String} containing implementation for a {@link Constructor} in generated class.
     */
    private String getMethodBody(Constructor constructor, Class<?> token) {
        return elementsSpaced(elementsLineSeparated(Tabs(1) + getClassName(token) +
                        elementsInBrackets(getParameters(constructor.getParameterTypes())),
                getThrowable(constructor.getExceptionTypes()) +
                        BLOCK_BEGIN, Tabs(2) + "super" +
                        elementsInBrackets(getParametersNumbers(constructor.getParameterTypes()))
                        + OPER_SEP, BLOCK_END));
    }

    /**
     * Writes a string with collection of exceptions. Prepends with "throws" if at least one {@link Exception}
     * could be thrown.
     * @param exceptionTypes possible exception types of a given method
     * @return {@link String} containing collection of {@link Exception}s, separated by {@link #COLLECTION_SEPARATOR}
     */
    private String getThrowable(Class[] exceptionTypes) {
        return exceptionTypes.length == 0 ? "" : "throws " +
                elementsToString(COLLECTION_SEPARATOR, exceptionTypes, Class::getName);
    }

    /**
     * Produces a {@link String} of method parameters. Uses {@link Indices} to get different identifier to
     * the instance of each parameter
     * @param parameterTypes parameters of method implementation of which is needed
     * @return {@link String} containing collection of pairs consisting of parameter type, {{@link #SPACE},}
     * unique identifier, separated by {@link #COLLECTION_SEPARATOR}
     */
    private String getParameters(Class[] parameterTypes) {
        Indices indices = new Indices();
        return parameterTypes.length == 0 ? "" :
                elementsInCollectionToString(parameterTypes,
                        parameter -> elementsSpaced(parameter.getCanonicalName(), "_" + indices.add()));
    }

    /**
     * Produces a {@link String} of method parameters identifiers. Uses {@link Indices} to get different identifier to
     * the instance of each parameter
     * @param parameterTypes parameters of method implementation of which is needed
     * @return {@link String} containing collection of  separated by {@link #SPACE}
     */
    private String getParametersNumbers(Class[] parameterTypes) {
        Indices indices = new Indices();
        return parameterTypes.length == 0 ? "" :
                elementsInCollectionToString(parameterTypes, parameter -> elementsSpaced("_" + indices.add()));
    }

    /**
     * Writes class modifiers. Gets {@link Modifier} of {@link Class} and writes a string containing all of them
     * excluding {@code Modifier.ABSTRACT}, {@code Modifier.INTERFACE}, {@code Modifier.STATIC}
     * and {@code Modifier.PROTECTED}
     * @param token {@link Class} modifiers of which are expected in return.
     * @return {@link String} containing modifiers of a given method excluding {@code Modifier.ABSTRACT},
     * {@code Modifier.INTERFACE}, {@code Modifier.STATIC} and {@code Modifier.PROTECTED}
     */
    private String getClassModifiers(Class<?> token) {
        return Modifier.toString(token.getModifiers() & ~Modifier.ABSTRACT &
                ~Modifier.INTERFACE & ~Modifier.STATIC & ~Modifier.PROTECTED);
    }

    /**
     * Writes method modifiers. Gets {@link Modifier} of {@link Method} and writes a string containing all of them
     * excluding {@code Modifier.ABSTRACT} and {@code Modifier.TRANSIENT}
     * @param m methods modifiers of which are expected in return.
     * @return {@link String} containing modifiers of a given method excluding {@code Modifier.ABSTRACT} and
     * {@code Modifier.TRANSIENT}
     */
    private String getMethodModifiers(Method m) {
        return Modifier.toString(m.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT);
    }

}

