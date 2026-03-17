package streams;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionalProgramming {
    static void main() {
        List<Integer> nums=new ArrayList<>(List.of(1,2,3,4,5,6,7,8,9,20));
        List<Integer>biggerThenFour=nums.stream().filter(n->n<=4).map(n->n*n)
                .collect(Collectors.toList());
        System.out.println(biggerThenFour);
    }
}
