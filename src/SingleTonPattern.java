public class SingleTonPattern {

        private static SingleTonPattern instance;

        private SingleTonPattern() {
            // private constructor
        }

        public static SingleTonPattern getInstance() {
            if (instance == null) {
                instance = new SingleTonPattern();
            }
            return instance;
        }
    }

