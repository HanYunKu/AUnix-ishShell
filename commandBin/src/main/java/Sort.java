import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code sort [-r] [<file>...]}: reads every line from all given files (or standard input, if
 * none given), concatenated in argument order, and prints them sorted lexicographically —
 * ascending by default, descending with {@code -r}.
 *
 * <p>Unlike {@code head}/{@code tail}, files are merged into one sorted stream rather than
 * processed (and headed) independently — that's how real {@code sort} behaves too.
 */
public class Sort extends ShellCommand {

    public Sort(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Sort.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        boolean reverse = false; // added
        int fileIndex = 0; // added
        List<String> lines = new ArrayList<>(); // added

        if (cmdArgs.length >= 1 && cmdArgs[0].equals("-r")) { // added
            reverse = true; // added
            fileIndex = 1; // added
        } // added

        if (fileIndex == cmdArgs.length) { // added
            readLines(System.in, lines); // added
        } else { // added
            for (int i = fileIndex; i < cmdArgs.length; i++) { // added
                String filename = cmdArgs[i]; // added

                try (InputStream input = getFileInput(Path.of(filename))) { // added
                    readLines(input, lines); // added
                } catch (IllegalArgumentException e) { // added
                    System.err.println(e.getMessage()); // added
                } // added
            } // added
        } // added

        Collections.sort(lines); // added

        if (reverse) { // added
            Collections.reverse(lines); // added
        } // added

        for (String line : lines) { // added
            System.out.print(line); // added
        } // added
    }

    private void readLines(InputStream input, List<String> lines) throws IOException { // added
        String line; // added

        while ((line = readLineRaw(input)) != null) { // added
            lines.add(line); // added
        } // added
    }
}
