package files;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class ReadFile {

    public void readFile() throws IOException {
        //Reading from a file
        try(
                BufferedReader reader=new BufferedReader(new FileReader("output.txt"))){
            String line;
            while ((line= reader.readLine())!=null){
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Another way
        try(
                Scanner reader=new Scanner(new File("output.txt"))){
            while (reader.hasNextLine()){
                System.out.println(reader.nextLine());
            }
        }

        //AnotherWay
        System.out.println(Files.readString(Paths.get("output.txt")));
    }
}
