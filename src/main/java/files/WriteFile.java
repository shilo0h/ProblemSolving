package files;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class WriteFile {

    public void writeToAFile() throws IOException {
        //Write to a file
        try (
                BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))) {
            writer.write("This is text in a file");
            writer.newLine();
            writer.write("Another line");
            writer.close();
        }
    }
}