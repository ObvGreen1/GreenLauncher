package me.obvgreen;

/**
 * Bootstrap main (not a JavaFX Application subclass) so the JVM doesn't demand
 * the JavaFX module path when running from the plain classpath.
 */
public class Launch {
    public static void main(String[] args) {
        Main.main(args);
    }
}
