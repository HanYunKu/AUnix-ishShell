import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A simple shell that mimics some basic features of bash. Java has no real fork/exec, so each
 * command runs as a separate forked {@code java} process (see {@link ProcessBuilder}) that locates
 * and loads the real command class at runtime via a {@link URLClassLoader} and
 * <a href="https://docs.oracle.com/javase/tutorial/reflect/">reflection</a> — {@code shell} has no
 * compile-time dependency on {@code commandBin}. See {@code README.md} for the full picture;
 * {@link #executeCommand} is where it comes together.
 */
public class Shell {
    public static final String COMMAND_PROMPT = "> ";
    private static final Path PATH = getPath();
    private final List<Pipeline> jobs = new ArrayList<>();
    /**
     * Represents the current working directory of the shell
     * (since the actual current working directory of a JVM cannot be changed).
     * This is the directory that command processes will be run in.
     */
    private Path cwd = Paths.get(System.getProperty("user.dir"));

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
        Shell shell = new Shell();
        if (args.length > 0) {
            shell.executeCommand(args[0]);
        } else {
            shell.runRepl();
        }
    }

    /**
     * Searches the PATH directory for a .class file corresponding to the given command.
     * Class names are converted from PascalCase to kebab-case before attempting to match.
     *
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

    public Path getCwd() {
        return cwd;
    }

    public void setCwd(Path path) {
        cwd = path;
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

            processInput(command);
        }
    }

    /**
     * Takes the input string and parses it to execute either a shell builtin or a pipeline of separate processes.
     *
     * @param input the command to execute
     * @return null if the input was a builtin command or if there was an error,
     * otherwise the pipeline that was executed (for testing purposes)
     */
    Pipeline processInput(String input) {
        if (input.isBlank()) {
            return null;
        }

        if (tryExecuteBuiltin(input)) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.startsWith(">")) {
            String fileName = trimmed.substring(1).trim();
            Path target = Paths.get(fileName);
            if (!target.isAbsolute()) {
                target = cwd.resolve(target).normalize();
            }
            try {
                Files.createFile(target);
            } catch (IOException ignored) {
            }
            return null;
        }

        try {
            Pipeline pipeline = new Pipeline(input);
            pipeline.executePipeline();
            if (pipeline.isBackground()) {
                jobs.add(pipeline);
            }
            return pipeline;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    /**
     * Executes the main method of the given command, passing along any additional args.
     * Should only be called when {@code Shell.main()} is called with non-zero number of args.
     * Does nothing if the given command is blank (only whitespace).
     *
     * <p><b>Given to you as a worked example</b> — this is where the class Javadoc's three ideas
     * (URI, ClassLoader, reflection) actually get used; you don't need to write this kind of code
     * yourself, but understanding it will help you debug {@link #findCommandClass} and, later,
     * extend the shell:
     * <ol>
     *     <li>{@link #findCommandClass} resolves the command name to a class name.</li>
     *     <li>{@link #PATH} (the directory holding {@code commandBin}'s compiled classes) is
     *     turned into a {@link URL}.</li>
     *     <li>A {@link URLClassLoader} opened on that URL loads the class — this is what makes
     *     the class reachable at all, since {@code commandBin} isn't on this module's classpath.</li>
     *     <li>{@code loadedClass.getMethod("main", String[].class)} then
     *     {@code .invoke(null, (Object) commandArgs)} calls that class's {@code main} via
     *     reflection — the same cast trick as {@code ShellCommand.start()}.</li>
     *     <li>Because this only ever runs inside a freshly-forked child JVM whose whole purpose
     *     was to run this one command, it reports its own exit status
     *     ({@code System.exit(0)}/{@code System.exit(1)}) like any Unix process would.</li>
     * </ol>
     * See {@code README.md} for a plainer walkthrough of the four ideas.
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
            System.exit(0);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Runs {@code input} as a builtin ({@code cd}, {@code kill}, {@code jobs}) if it is one.
     * Builtins run in the main shell process, not forked, since they mutate the shell's own state
     * ({@code cwd}, {@code jobs}).
     *
     * @param input the command string
     * @return true if the command was a builtin, false otherwise
     */
    private boolean tryExecuteBuiltin(String input) {
        String[] args = input.trim().split("\\s+");

        if (args[0].equals("cd")) {
            if (args.length > 1) {
                changeDirectory(args[1]);
            }
            return true;
        }

        if (args[0].equals("jobs")) {
            listJobs();
            return true;
        }

        if (args[0].equals("kill")) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                try {
                    int id = Integer.parseInt(args[i]);
                    if (id <= 0) {
                        System.err.println("kill: arguments must be job IDs");
                        return true;
                    }
                    ids.add(id);
                } catch (NumberFormatException e) {
                    System.err.println("kill: arguments must be job IDs");
                    return true;
                }
            }
            killJobs(ids);
            return true;
        }

        return false;
    }

    /**
     * Builds the {@code ProcessBuilder} for
     * {@code java -classpath <Shell's own classes> Shell "<command>"} — the fork workaround
     * described in the class Javadoc. Sets the child's working directory to {@link #cwd}, and
     * redirects output to a file if {@code command} ends in {@code > <file>}.
     *
     * @param command the string passed to the new {@code Shell} process as an argument
     *                (after removing the redirect if there is one)
     * @return the {@code ProcessBuilder} for the "forked" process
     * @throws IllegalArgumentException if a redirect file is given and it is a directory
     */
    ProcessBuilder buildForkedShell(String command) throws IllegalArgumentException {
        String commandToRun = command.trim();
        Path redirectTarget = null;

        int redirectIndex = commandToRun.lastIndexOf(">");
        if (redirectIndex >= 0) {
            String fileName = commandToRun.substring(redirectIndex + 1).trim();
            commandToRun = commandToRun.substring(0, redirectIndex).trim();
            redirectTarget = Paths.get(fileName);
            if (!redirectTarget.isAbsolute()) {
                redirectTarget = cwd.resolve(redirectTarget).normalize();
            }
            if (Files.isDirectory(redirectTarget)) {
                throw new IllegalArgumentException(fileName + ": Is a directory");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-cp",
                getCurrentClassPath().toString(),
                "Shell",
                commandToRun
        );
        pb.directory(cwd.toFile());
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        if (redirectTarget == null) {
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        } else {
            pb.redirectOutput(redirectTarget.toFile());
        }

        return pb;
    }

    /**
     * Changes the shell's working directory by updating the value of the {@code cwd} field.
     *
     * @param pathString the absolute or relative path string to change to
     */
    void changeDirectory(String pathString) {
        Path path = Paths.get(pathString);
        if (!path.isAbsolute()) {
            path = cwd.resolve(path).normalize();
        }

        if (!Files.exists(path)) {
            System.err.println("cd: " + pathString + ": No such file or directory");
            return;
        }

        cwd = path.toAbsolutePath().normalize();
    }

    /**
     * Prints background jobs and their status to standard out.
     * A job should only be removed from the list if it was shown in a previous call with the "Done" or "Terminated" status.
     * Job numbering does not restart unless the job list becomes empty.
     */
    void listJobs() {
        String formatSpecifier = "%-10s%-20s %s%n";

        for (int i = 0; i < jobs.size(); i++) {
            Pipeline job = jobs.get(i);
            if (job != null) {
                System.out.printf(formatSpecifier, "[" + (i + 1) + "]", job.getStatus(), job.getPipelineCommand());
            }
        }

        for (int i = 0; i < jobs.size(); i++) {
            Pipeline job = jobs.get(i);
            if (job != null && (job.isDone() || job.isTerminated())) {
                jobs.set(i, null);
            }
        }

        boolean anyJobs = false;
        for (Pipeline job : jobs) {
            if (job != null) {
                anyJobs = true;
                break;
            }
        }
        if (!anyJobs) {
            jobs.clear();
        }
    }

    /**
     * Kills the given jobs. The ids must be existing job ids.
     *
     * @param ids the ids of the jobs to kill
     */
    void killJobs(List<Integer> ids) {
        for (int id : ids) {
            int index = id - 1;
            if (index < 0 || index >= jobs.size() || jobs.get(index) == null) {
                System.err.println("kill: " + id + ": no such job");
            } else {
                jobs.get(index).killPipeline();
            }
        }
    }

    /**
     * Represents a pipeline: a list of commands that run as separate processes, with their inputs and outputs linked
     * by pipes.
     */
    public class Pipeline {
        private final String pipelineCommand;
        private final List<ProcessBuilder> pbs;
        private final boolean isBackground;
        private List<Process> processes = null;
        private boolean isDone = false;
        private boolean terminated = false;

        /**
         * Splits {@code input} on {@code |}, builds a forked process for each subcommand via
         * {@link #buildForkedShell}, and wires the first process's stdin and (unless already
         * redirected to a file) the last process's stdout to this shell's own. A trailing
         * {@code &} marks the pipeline as a background job.
         *
         * @param input the string command for the pipeline
         */
        public Pipeline(String input) throws IllegalArgumentException {
            pipelineCommand = input.trim();
            String commandString = pipelineCommand;
            isBackground = commandString.endsWith("&");
            if (isBackground) {
                commandString = commandString.substring(0, commandString.length() - 1).trim();
            }

            pbs = new ArrayList<>();
            String[] commands = commandString.split("\\|");
            for (String command : commands) {
                pbs.add(buildForkedShell(command.trim()));
            }

            if (!pbs.isEmpty()) {
                pbs.getFirst().redirectInput(ProcessBuilder.Redirect.INHERIT);
                ProcessBuilder last = pbs.getLast();
                if (last.redirectOutput() == ProcessBuilder.Redirect.PIPE) {
                    last.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
            }
        }

        /**
         * Starts every process in the pipeline, connected by real OS pipes via
         * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessBuilder.html#startPipeline(java.util.List)">ProcessBuilder#startPipeline</a>,
         * and waits for them to finish unless this is a background job.
         *
         * @return the list of processes in the pipeline
         */
        public List<Process> executePipeline() throws Exception {
            processes = ProcessBuilder.startPipeline(pbs);
            if (!isBackground) {
                waitFor();
            }
            return processes;
        }

        /**
         * Waits for all processes in the pipeline to complete.
         * Does nothing if {@code executePipeline()} has not been called.
         */
        void waitFor() throws InterruptedException {
            if (processes == null) {
                return;
            }
            for (Process process : processes) {
                process.waitFor();
            }
            isDone = true;
        }

        /**
         * Terminates all processes in the pipeline. {@link Process#destroy()} only requests
         * termination asynchronously, so this also waits for each process to actually exit
         * before returning.
         */
        public void killPipeline() {
            if (processes == null) {
                terminated = true;
                isDone = true;
                return;
            }

            for (Process process : processes) {
                process.destroy();
            }
            for (Process process : processes) {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            terminated = true;
            isDone = true;
        }

        /**
         * Checks if all processes in the pipeline have finished execution.
         *
         * @return false if any processes are still alive, otherwise true
         */
        public boolean isDone() {
            if (processes == null) {
                return isDone;
            }
            for (Process process : processes) {
                if (process.isAlive()) {
                    return false;
                }
            }
            isDone = true;
            return true;
        }

        /**
         * Checks if the pipeline command is a background job.
         *
         * @return true if pipeline runs in the background, false if runs in the foreground
         */
        public boolean isBackground() {
            return isBackground;
        }

        /**
         * Checks if the pipeline command was terminated with a call to {@code killPipeline}.
         *
         * @return true if {@code killPipeline} was called, otherwise false
         */
        public boolean isTerminated() {
            return terminated;
        }

        public String getPipelineCommand() {
            return pipelineCommand;
        }

        public List<ProcessBuilder> getPbs() {
            return pbs;
        }

        public List<Process> getProcesses() {
            return processes;
        }

        /** This job's {@code jobs} status: {@code "Terminated"}, {@code "Done"}, or {@code "Running"}. */
        public String getStatus() {
            return terminated ? "Terminated" : (isDone() ? "Done" : "Running");
        }

        @Override
        public String toString() {
            return String.format("%-20s %s", getStatus(), pipelineCommand);
        }
    }
}
