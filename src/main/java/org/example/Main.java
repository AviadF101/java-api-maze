package org.example;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // הדרך הרשמית והבטוחה ביותר ב-Java להזניק חלונות גרפיים של Swing
        SwingUtilities.invokeLater(() -> {
            MazeApp app = new MazeApp();
            app.setVisible(true); // שורת הקסם שמציגה את החלון על המסך!
        });
    }
}