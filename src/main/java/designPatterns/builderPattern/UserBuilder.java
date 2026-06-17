package designPatterns.builderPattern;

import lombok.Builder;

@Builder
public class UserBuilder {
    private String name;
    private int age;
    public UserBuilder(String name,int age){
        this.name=name;
        this.age=age;
    }

    public UserBuilder(){

    }

    @Override
    public String toString() {
        return "UserBuilder{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
