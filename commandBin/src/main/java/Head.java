import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * {@code head [-n <count>] [<file>...]}: prints the first {@code count} lines (default {@code 10})
 * of each file.
 *
 * <p>With more than one file, each file's output is preceded by an {@code "==> <file> <=="}
 * header line, with a blank line separating consecutive files' output (no header for a single
 * file or standard input).
 */
public class Head extends ShellCommand {
    private static final int DEFAULT_COUNT = 10;

    public Head(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Head.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        int count = DEFAULT_COUNT; // herefixed
        int fileIndex = 0; // herefixed

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-n")) { // herefixed
            if (cmdArgs.length < 2) { // herefixed
                System.err.println("Usage: head [-n <count>] [<file>...]"); // herefixed
                return; // herefixed
            } // herefixed

            try { // herefixed
                count = Integer.parseInt(cmdArgs[1]); // herefixed
            } catch (NumberFormatException e) { // herefixed
                System.err.println("Usage: head [-n <count>] [<file>...]"); // herefixed
                return; // herefixed
            } // herefixed

            fileIndex = 2; // herefixed
        }

        if (fileIndex == cmdArgs.length) {
            printHead(System.in, count);
            return;
        }

        boolean multipleFiles = cmdArgs.length - fileIndex > 1; // herefixed
        boolean printedFile = false; // herefixed

        for (int i = fileIndex; i < cmdArgs.length; i++) {
            String filename = cmdArgs[i];

            try (InputStream input = getFileInput(Path.of(filename))) {
                if (multipleFiles) { // herefixed
                    if (printedFile) { // herefixed
                        System.out.println(); // herefixed
                    } // herefixed

                    System.out.println("==> " + filename + " <=="); // herefixed
                } // herefixed

                printHead(input, count); // herefixed
                printedFile = true; // herefixed
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }

    
    


    }

    private void printHead(InputStream input, int count) throws IOException {
        String line;
        int linesPrinted = 0;

        while (linesPrinted < count && (line = readLineRaw(input)) != null) {
            System.out.print(line);
            linesPrinted++;
        }

        
    }









}
