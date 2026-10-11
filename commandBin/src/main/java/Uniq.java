import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code uniq [-c] [<file>]}: collapses runs of <b>adjacent</b> duplicate lines into a single
 * line — non-adjacent duplicates are left alone, same as real {@code uniq} (pair with
 * {@code sort} first to dedupe an entire file). With {@code -c}, each output line is prefixed
 * with the number of times it occurred in its run.
 *
 * <p>Unlike the other commands, {@code uniq} takes at most <b>one</b> file argument — that's a
 * real constraint of the actual Unix command, not a simplification for this project.
 */
public class Uniq extends ShellCommand {

    public Uniq(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Uniq.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        boolean showCount = false; // added
        int fileIndex = 0; // added

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-c")) { // added
            showCount = true; // added
            fileIndex = 1; // added
        } // added

        if (cmdArgs.length - fileIndex > 1) { // added
            System.err.println("Usage: uniq [-c] [<file>]"); // added
            return; // added
        } // added

        if (fileIndex == cmdArgs.length) { // added
            printUniq(System.in, showCount); // added
            return; // added
        } // added

        String filename = cmdArgs[fileIndex]; // added

        try (InputStream input = getFileInput(Path.of(filename))) { // added
            printUniq(input, showCount); // added
        } catch (IllegalArgumentException e) { // added
            System.err.println(e.getMessage()); // added
        } // added
    }

    private void printUniq(InputStream input, boolean showCount) throws IOException { // added
        String previousLine = null; // added
        int count = 0; // added
        String line; // added

        while ((line = readLineRaw(input)) != null) { // added
            if (previousLine == null) { // added
                previousLine = line; // added
                count = 1; // added
            } else if (previousLine.equals(line)) { // added
                count++; // added
            } else { // added
                printLine(previousLine, count, showCount); // added
                previousLine = line; // added
                count = 1; // added
            } // added
        } // added

        if (previousLine != null) { // added
            printLine(previousLine, count, showCount); // added
        } // added
    }

    private void printLine(String line, int count, boolean showCount) { // added
        if (showCount) { // added
            System.out.print(count + " " + line); // added
        } else { // added
            System.out.print(line); // added
        } // added
    }
}
