package streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FunctionalProgramming {
    static void main() {
        //Is the way to make use of Java stream APIs to make the
        //so that we write less code

        List<Integer> nums = new ArrayList<>();
        nums.add(1);
        nums.add(2);
        nums.add(3);

        // Create a stream
        Stream<Integer> stream2 = nums.stream();

        // Map each number to its square and collect to a list
        List<Integer> nums3 = stream2.map(n -> n * n)
                .collect(Collectors.toList());


        List<String> items = Arrays.asList("Apple", "Banana", "Cherry", "Date");

            // Filter strings with length > 5
                    List<String> longItems = items.stream()
                            .filter(s -> s.length() > 5)
                            .collect(Collectors.toList());

                    longItems.forEach(System.out::println);
            // Output: Banana, Cherry

        // Print result
        System.out.println(nums3); // Output: [1, 4, 9]
    }
}
