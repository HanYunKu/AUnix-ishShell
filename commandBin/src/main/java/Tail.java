import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code tail [-n <count>] [<file>...]}: prints the last {@code count} lines (default {@code 10})
 * of each file.
 *
 * <p>With more than one file, each file's output is preceded by an {@code "==> <file> <=="}
 * header line, with a blank line separating consecutive files' output (no header for a single
 * file or standard input).
 */
public class Tail extends ShellCommand {
    private static final int DEFAULT_COUNT = 10;

    public Tail(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Tail.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        int count = DEFAULT_COUNT; // added
        int fileIndex = 0; // added

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-n")) { // added
            if (cmdArgs.length < 2) { // added
                System.err.println("Usage: tail [-n <count>] [<file>...]"); // added
                return; // added
            } // added

            try { // added
                count = Integer.parseInt(cmdArgs[1]); // added
            } catch (NumberFormatException e) { // added
                System.err.println("Usage: tail [-n <count>] [<file>...]"); // added
                return; // added
            } // added

            fileIndex = 2; // added
        } // added

        if (fileIndex == cmdArgs.length) { // added
            printTail(System.in, count); // added
            return; // added
        } // added

        boolean multipleFiles = cmdArgs.length - fileIndex > 1; // added
        boolean printedFile = false; // added

        for (int i = fileIndex; i < cmdArgs.length; i++) { // added
            String filename = cmdArgs[i]; // added

            try (InputStream input = getFileInput(Path.of(filename))) { // added
                if (multipleFiles) { // added
                    if (printedFile) { // added
                        System.out.println(); // added
                    } // added

                    System.out.println("==> " + filename + " <=="); // added
                } // added

                printTail(input, count); // added
                printedFile = true; // added
            } catch (IllegalArgumentException e) { // added
                System.err.println(e.getMessage()); // added
            } // added
        } // added
    }

    private void printTail(InputStream input, int count) throws IOException { // added
        List<String> lines = new ArrayList<>(); // added
        String line; // added

        while ((line = readLineRaw(input)) != null) { // added
            lines.add(line); // added
        } // added

        int start = lines.size() - count; // added
        if (start < 0) { // added
            start = 0; // added
        } // added

        for (int i = start; i < lines.size(); i++) { // added
            System.out.print(lines.get(i)); // added
        } // added
    }
}
