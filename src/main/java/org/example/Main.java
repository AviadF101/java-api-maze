package org.example;

public class Main {
    public static void main(String[] args) {
        new Thread(() -> {

            MazeApp app = new MazeApp();
            app.setVisible(true);
        }).start();
    }
}