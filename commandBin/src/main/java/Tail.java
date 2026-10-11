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
        int count = DEFAULT_COUNT;
        int fileIndex = 0;

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-n")) {
            if (cmdArgs.length < 2) {
                System.err.println("Usage: tail [-n <count>] [<file>...]");
                return;
            }

            try {
                count = Integer.parseInt(cmdArgs[1]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: tail [-n <count>] [<file>...]");
                return;
            }

            fileIndex = 2;
        }

        if (fileIndex == cmdArgs.length) {
            printTail(System.in, count);
            return;
        }

        boolean multipleFiles = cmdArgs.length - fileIndex > 1;
        boolean printedFile = false;

        for (int i = fileIndex; i < cmdArgs.length; i++) {
            String filename = cmdArgs[i];

            try (InputStream input = getFileInput(Path.of(filename))) {
                if (multipleFiles) {
                    if (printedFile) {
                        System.out.println();
                    }

                    System.out.println("==> " + filename + " <==");
                }

                printTail(input, count);
                printedFile = true;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void printTail(InputStream input, int count) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;

        while ((line = readLineRaw(input)) != null) {
            lines.add(line);
        }

        int start = lines.size() - count;
        if (start < 0) {
            start = 0;
        }

        for (int i = start; i < lines.size(); i++) {
            System.out.print(lines.get(i));
        }
    }
}
