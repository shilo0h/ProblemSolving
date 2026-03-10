package designPatterns.strategyPattern;

public class SortService {

    private SortStrategy sortStrategy;

    public SortService(SortStrategy sortStrategy){
        this.sortStrategy=sortStrategy;
    }

    public void sort(int[]nums){
        sortStrategy.sort(nums);
    }
}
