package designPatterns.decoratorPattern;

public class MilkDecorator implements Coffee{
    private final Coffee coffee;

    public MilkDecorator(Coffee coffee){
        this.coffee=coffee;
    }
    @Override
    public double cost() {
        return coffee.cost()+2;
    }
}