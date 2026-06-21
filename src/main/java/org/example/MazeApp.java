package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;

public class MazeApp extends JFrame {
    private final Config config = new Config();
    private int mazeWidth = 30, mazeHeight = 30;
    private boolean[][] mazeMatrix;
    private List<Point> solutionPath = new ArrayList<>();
    private final Set<Point> animatedPath = new LinkedHashSet<>();
    private boolean isAnimating = false;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    // תוויות תצוגה ושדות קלט בשורות קצרות ונקיות
    private final JPanel wallColorBox = new JPanel(), pathColorBox = new JPanel(), gridColorBox = new JPanel();
    private final JLabel gridStatusLabel = new JLabel("-"), delayLabel = new JLabel("-");
    private final JTextField widthField = new JTextField("30", 4), heightField = new JTextField("30", 4);
    private final JButton getMazeButton = new JButton("GET MAZE"), checkSolutionButton = new JButton("Check Solution");
    private final MazePanel mazePanel = new MazePanel(config);

    // // משימה 14: משתני מונים עבור הלחיצות
    private int refreshCount = 0, getMazeCount = 0;

    // // משימות 5 ו-14: תוויות טקסט חדשות להצגה על המסך
    private final JLabel solutionLengthLabel = new JLabel("Solution length: -");
    private final JLabel countsLabel = new JLabel("Refresh: 0 | Get Maze: 0");

    public MazeApp() {
        super("תרגיל Java — מבוך ויזואלי מ-API");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 700);
        setLocationRelativeTo(null);

        // --- מסך 1: הגדרות ---
        // קביעת גודל אחיד למלבני הצבע כדי שייראו טוב במסך ההגדרות
        Dimension boxSize = new Dimension(40, 20);
        wallColorBox.setPreferredSize(boxSize);
        pathColorBox.setPreferredSize(boxSize);
        gridColorBox.setPreferredSize(boxSize);

        JPanel setupPanel = new JPanel(new GridBagLayout());
        setupPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 12, 6, 12); gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("הגדרות הציור מהשרת וממדי המבוך", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; setupPanel.add(title, gbc);

        gbc.gridy = 1; JButton btnRef = new JButton("רענון הגדרות מהשרת (Refresh Config)");
        btnRef.addActionListener(e -> fetchConfig()); setupPanel.add(btnRef, gbc);

        gbc.gridwidth = 1;
        // במקום להוסיף את ה-Labels הישנים, אנחנו מוסיפים את ה-Panels של הצבע
        gbc.gridy = 2; gbc.gridx = 0; setupPanel.add(new JLabel("צבע הקירות שנקבע:"), gbc); gbc.gridx = 1; setupPanel.add(wallColorBox, gbc);
        gbc.gridy = 3; gbc.gridx = 0; setupPanel.add(new JLabel("צבע נתיב הפתרון:"), gbc); gbc.gridx = 1; setupPanel.add(pathColorBox, gbc);
        gbc.gridy = 4; gbc.gridx = 0; setupPanel.add(new JLabel("האם לצייר קווי רשת:"), gbc); gbc.gridx = 1; setupPanel.add(gridStatusLabel, gbc);
        gbc.gridy = 5; gbc.gridx = 0; setupPanel.add(new JLabel("צבע הרשת:"), gbc); gbc.gridx = 1; setupPanel.add(gridColorBox, gbc);
        gbc.gridy = 6; gbc.gridx = 0; setupPanel.add(new JLabel("זמן המתנה באנימציה:"), gbc); gbc.gridx = 1; setupPanel.add(delayLabel, gbc);
        gbc.gridy = 7; gbc.gridx = 0; setupPanel.add(new JLabel("רוחב המבוך הרצוי (Width):"), gbc); gbc.gridx = 1; setupPanel.add(widthField, gbc);
        gbc.gridy = 8; gbc.gridx = 0; setupPanel.add(new JLabel("גובה המבוך הרצוי (Height):"), gbc); gbc.gridx = 1; setupPanel.add(heightField, gbc);

        gbc.gridy = 9; gbc.gridx = 0; gbc.gridwidth = 2; getMazeButton.setFont(new Font("Arial", Font.BOLD, 14));
        getMazeButton.addActionListener(e -> handleGetMaze()); setupPanel.add(getMazeButton, gbc);

        // --- מסך 2: מבוך סופי ---
        JPanel gamePanel = new JPanel(new BorderLayout());
        JPanel gameControlPanel = new JPanel(new FlowLayout());

        checkSolutionButton.addActionListener(e -> handleCheckSolution());
        gameControlPanel.add(checkSolutionButton);

        // // משימה 5: הוספת תווית אורך המסלול לסרגל המשחק
        gameControlPanel.add(solutionLengthLabel);

        JButton btnBack = new JButton("חזור להגדרות");
        btnBack.addActionListener(e -> { if (!isAnimating) cardLayout.show(mainContainer, "SETUP"); });
        gameControlPanel.add(btnBack);

        // // משימה 14: הוספת תווית המונים לסרגל המשחק כדי שהמרצה יראה את זה
        gameControlPanel.add(countsLabel);

        gamePanel.add(gameControlPanel, BorderLayout.NORTH);
        gamePanel.add(mazePanel, BorderLayout.CENTER);

        mainContainer.add(setupPanel, "SETUP"); mainContainer.add(gamePanel, "GAME");
        add(mainContainer); cardLayout.show(mainContainer, "SETUP");
        setVisible(true);
        fetchConfig();
    }

    // פונקציית עזר שמקצרת ומנקה את המרת הצבעים לטקסט
    private String toHex(Color c) {
        return "#" + Integer.toHexString(c.getRGB()).substring(2).toUpperCase();
    }

    private void fetchConfig() {
        // // משימה 14: הגדלת מונה הרענון ועדכון הטקסט על המסך
        refreshCount++;
        countsLabel.setText("Refresh: " + refreshCount + " | Get Maze: " + getMazeCount);

        new Thread(() -> {
            try {
                String url = "https://backend-qcf9.onrender.com/fm1/get-render-config?t=" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setUseCaches(false);

                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line); r.close();

                    config.parseJson(sb.toString());

                    SwingUtilities.invokeLater(() -> {
                        // צביעת המלבנים ישירות באובייקט הצבע שחזר מהשרת
                        wallColorBox.setBackground(config.wallCellColor);
                        pathColorBox.setBackground(config.pathColor);
                        gridColorBox.setBackground(config.gridColor);

                        gridStatusLabel.setText(config.drawGrid ? "כן (True)" : "לא (False)");
                        delayLabel.setText(config.animationDelayMs + " ms");

                        // רענון גרפי קטן כדי לוודא שג'אווה צובעת את הריבועים מייד על המסך
                        mainContainer.repaint();
                    });
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void handleGetMaze() {
        // // משימה 14: הגדלת מונה בקשת המבוך ועדכון הטקסט על המסך
        getMazeCount++;
        countsLabel.setText("Refresh: " + refreshCount + " | Get Maze: " + getMazeCount);

        try {
            mazeWidth = Integer.parseInt(widthField.getText().trim());
            mazeHeight = Integer.parseInt(heightField.getText().trim());

            if (mazeWidth < 5 || mazeWidth > 100) {
                mazeWidth = 30; SwingUtilities.invokeLater(() -> widthField.setText("30"));
            }
            if (mazeHeight < 5 || mazeHeight > 100) {
                mazeHeight = 30; SwingUtilities.invokeLater(() -> heightField.setText("30"));
            }
        } catch (Exception e) {
            mazeWidth = 30; mazeHeight = 30;
            SwingUtilities.invokeLater(() -> { widthField.setText("30"); heightField.setText("30"); });
        }

        solutionPath.clear();
        animatedPath.clear();
        // // איפוס תווית המסלול בטעינת מבוך חדש
        SwingUtilities.invokeLater(() -> solutionLengthLabel.setText("Solution length: -"));

        new Thread(() -> {
            try {
                String url = String.format("https://backend-qcf9.onrender.com/fm1/get-maze-image?width=%d&height=%d", mazeWidth, mazeHeight);
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                if (c.getResponseCode() == 200) {
                    BufferedImage img = ImageIO.read(c.getInputStream());
                    int iw = img.getWidth(), ih = img.getHeight();
                    mazeMatrix = new boolean[mazeHeight][mazeWidth];

                    for (int y = 0; y < mazeHeight; y++) {
                        for (int x = 0; x < mazeWidth; x++) {
                            int sx = (mazeWidth > 1) ? (x * (iw - 1)) / (mazeWidth - 1) : 0;
                            int sy = (mazeHeight > 1) ? (y * (ih - 1)) / (mazeHeight - 1) : 0;
                            int rgb = img.getRGB(sx, sy);
                            mazeMatrix[y][x] = (((rgb >> 16) & 0xFF) == 255 && ((rgb >> 8) & 0xFF) == 255 && (rgb & 0xFF) == 255);
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        mazePanel.updateData(mazeMatrix, animatedPath);
                        cardLayout.show(mainContainer, "GAME");
                    });
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void handleCheckSolution() {
        if (isAnimating) return;
        animatedPath.clear();
        solutionPath = MazeSolver.solve(mazeMatrix);

        if (solutionPath.isEmpty()) { JOptionPane.showMessageDialog(this, "No solution found"); return; }

        // // משימה 5: עדכון אורך המסלול לפי כמות הנקודות ב-ArrayList
        solutionLengthLabel.setText("Solution length: " + solutionPath.size());

        isAnimating = true; checkSolutionButton.setEnabled(false);
        new Thread(() -> {
            try {
                for (Point p : solutionPath) {
                    animatedPath.add(p); SwingUtilities.invokeLater(() -> mazePanel.repaint());
                    Thread.sleep(config.animationDelayMs);
                }
            } catch (Exception e) { e.printStackTrace(); }
            finally { isAnimating = false; SwingUtilities.invokeLater(() -> checkSolutionButton.setEnabled(true)); }
        }).start();
    }

    public static void main(String[] args) {
        new Thread(() -> {
            MazeApp app = new MazeApp();
            app.setVisible(true);
        }).start();
    }
}