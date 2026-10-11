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
        boolean showCount = false;
        int fileIndex = 0;

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-c")) {
            showCount = true;
            fileIndex = 1;
        }

        if (cmdArgs.length - fileIndex > 1) {
            System.err.println("Usage: uniq [-c] [<file>]");
            return;
        }

        if (fileIndex == cmdArgs.length) {
            printUniq(System.in, showCount);
            return;
        }

        String filename = cmdArgs[fileIndex];

        try (InputStream input = getFileInput(Path.of(filename))) {
            printUniq(input, showCount);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void printUniq(InputStream input, boolean showCount) throws IOException {
        String previousLine = null;
        int count = 0;
        String line;

        while ((line = readLineRaw(input)) != null) {
            if (previousLine == null) {
                previousLine = line;
                count = 1;
            } else if (previousLine.equals(line)) {
                count++;
            } else {
                printLine(previousLine, count, showCount);
                previousLine = line;
                count = 1;
            }
        }

        if (previousLine != null) {
            printLine(previousLine, count, showCount);
        }
    }

    private void printLine(String line, int count, boolean showCount) {
        if (showCount) {
            System.out.print(count + " " + line);
        } else {
            System.out.print(line);
        }
    }
}
