import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * A very simple shell: a REPL that reads one command at a time and runs it. It can only run one
 * command at a time — no pipes, no redirects, no background jobs, no {@code cd} (those all arrive
 * in Part B). Running a command means dynamically loading and invoking its class from
 * {@code commandBin}, since {@code shell} has no compile-time dependency on that module (check
 * {@code shell/pom.xml} — there's no such dependency, so {@code Shell.java} can't write
 * {@code new Wc(args)} directly). See {@code README.md} for why that needs a
 * {@link URLClassLoader} and <a href="https://docs.oracle.com/javase/tutorial/reflect/">reflection</a>;
 * {@link #executeCommand} (given to you) is the worked example.
 */
public class Shell {
    public static final String COMMAND_PROMPT = "> ";
    private static final Path PATH = getPath();

    /**
     * Returns the path of the {@code Shell.class} file, via {@code getProtectionDomain()
     * .getCodeSource().getLocation()} and a
     * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html">URI</a>
     * conversion ({@link Paths#get(java.net.URI)} needs a {@code URI}, not a {@link URL}).
     */
    public static Path getCurrentClassPath() {
        try {
            return Paths.get(Shell.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Absolute path of {@code commandBin}'s compiled classes, computed relative to
     * {@link #getCurrentClassPath} so it isn't hardcoded to one machine's checkout location.
     */
    public static Path getPath() {
        return getCurrentClassPath().resolve("../../../commandBin/target/classes").toAbsolutePath().normalize();
    }

    public static void main(String[] args) {
        (new Shell()).runRepl();
    }

    /**
     * Runs the Read-Eval-Print Loop of the Shell. The command "exit" ends the loop.
     */
    public void runRepl() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(COMMAND_PROMPT);

            if (!scanner.hasNextLine()) {
                break;
            }

            String command = scanner.nextLine();

            if (command.equals("exit")) {
                break;
            }

            executeCommand(command);
        }
    }

    /**
     * Executes the main method of the given command, passing along any additional args.
     * Does nothing if the given command is blank (only whitespace).
     *
     * <p><b>Given to you as a worked example</b> — this is where the class Javadoc's ideas
     * (URI, ClassLoader, reflection) actually get used; you don't need to write this kind of code
     * yourself, but understanding it will help you debug {@link #findCommandClass}:
     * <ol>
     *     <li>{@link #findCommandClass} resolves the command name to a class name.</li>
     *     <li>{@link #PATH} (the directory holding {@code commandBin}'s compiled classes) is
     *     turned into a {@link URL}.</li>
     *     <li>A {@link URLClassLoader} opened on that URL loads the class — this is what makes
     *     the class reachable at all, since {@code commandBin} isn't on this module's classpath.</li>
     *     <li>{@code loadedClass.getMethod("main", String[].class)} then
     *     {@code .invoke(null, (Object) commandArgs)} calls that class's {@code main} via
     *     reflection — the same cast trick as {@code ShellCommand.start()}.</li>
     * </ol>
     * See {@code README.md} for a plainer walkthrough.
     *
     * @param command the command to execute
     */
    void executeCommand(String command) {
        if (command.isBlank()) {
            return;
        }
        String[] commandArgs = command.split("\\s+");
        String commandName = commandArgs[0];
        try {
            // Get Class name for the command
            String commandClass = findCommandClass(commandName);
            // Create URL pointing to the classpath location
            File file = new File(String.valueOf(PATH));
            URL url = file.toURI().toURL();

            // Define the isolated ClassLoader
            try (URLClassLoader loader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader())) {
                // Load the target class
                Class<?> loadedClass = Class.forName(commandClass, true, loader);
                java.lang.reflect.Method mainMethod = loadedClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) commandArgs);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Searches the PATH directory for a .class file corresponding to the given command.
     * Class names are converted from PascalCase to kebab-case before attempting to match.
     * @param command the command name
     * @return the matched class name
     * @throws Exception if a match class cannot be found
     */
    private static String findCommandClass(String command) throws Exception {
        File commandDirectory = PATH.toFile();
        File[] files = commandDirectory.listFiles();

        if (files != null) {
            for (File file : files) {
                String filename = file.getName();

                if (filename.endsWith(".class")) {
                    String className = filename.substring(0, filename.length() - 6);
                    String commandName = classNameToCommandName(className);

                    if (commandName.equals(command)) {
                        return className;
                    }
                }
            }
        }

        throw new Exception(command + ": command not found");
    }

    /**
     * Converts a PascalCase class name to kebab-case (e.g. {@code "WordCount"} to
     * {@code "word-count"}), splitting on lower-to-upper boundaries via
     * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html">lookaround regex</a>
     * so acronym runs stay together.
     *
     * @param className the java class name
     * @return the kebab-case equivalent command name
     */
    public static String classNameToCommandName(String className) {
        // Split on uppercase letter boundaries, handling acronyms safely
        String[] words = className.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        // Lowercase and join with hyphens
        return Arrays.stream(words)
                .map(String::toLowerCase)
                .collect(Collectors.joining("-"));
    }
}
