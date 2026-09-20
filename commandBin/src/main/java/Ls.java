import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code ls [<path>...]}: lists files and directories. With no arguments, lists the current
 * directory.
 *
 * <p>Files print first (sorted by name), then directories (sorted by name), blank-line separated.
 * A directory's contents get a {@code "<dir>:"} header only when more than one argument was given.
 */
public class Ls extends ShellCommand {

    public Ls(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Ls.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        List<Path> files = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        String[] paths;

        if (cmdArgs.length ==0){
            paths = new String[1];
            paths[0] = ".";
        }
        else {
            paths = cmdArgs;
        }

        for (String name : paths){
            Path path = Path.of(name);

            if (!Files.exists(path)){
                System.err.println("ls: " + path + ": No such file or directory");
            }
            else if (Files.isDirectory(path)){
                directories.add(path);
            }
            else {
                files.add(path);
            }

        } 

        Collections.sort(files);
        Collections.sort(directories);

        for (Path file : files){
            System.out.println(file);
        }

        if (!files.isEmpty() && !directories.isEmpty()){
            System.out.println();
        }

        for (int i =0; i < directories.size();i++) {
            Path directory = directories.get(i);

            if (i > 0) {
                System.out.println();
            }

            if (paths.length > 1) {
                System.out.println(directory + ":");
            }

            File[] contents = directory.toFile().listFiles();
            if (contents != null) {
                Arrays.sort(contents);

                for (File item : contents) {
                    System.out.println(item.getName());
                }
            }

        }
        

    }

}
