package designPatterns.builderPattern;
class User {

    private String name;
    private int age;

    private User(UserBuilder builder) {
        this.name = builder.name;
        this.age = builder.age;
    }

    public static class UserBuilder {

        private String name;
        private int age;

        public UserBuilder name(String name) {
            this.name = name;
            return this;
        }

        public UserBuilder age(int age) {
            this.age = age;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}