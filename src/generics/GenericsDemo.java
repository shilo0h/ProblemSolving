package generics;

import lombok.Getter;

@Getter
public class GenericsDemo<T> {

    private T data;

    public GenericsDemo(T data){
        this.data=data;
    }

}
