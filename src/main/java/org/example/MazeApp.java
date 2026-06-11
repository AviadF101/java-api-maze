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

    // תוויות תצוגה בלבד
    private final JLabel wallColorLabel = new JLabel("-"), pathColorLabel = new JLabel("-");
    private final JLabel gridStatusLabel = new JLabel("-"), gridColorLabel = new JLabel("-"), delayLabel = new JLabel("-");

    // שדות קלט וכפתורים
    private final JTextField widthField = new JTextField("30", 4), heightField = new JTextField("30", 4);
    private final JButton getMazeButton = new JButton("GET MAZE"), checkSolutionButton = new JButton("Check Solution");
    private final MazePanel mazePanel = new MazePanel(config);

    public MazeApp() {
        super("תרגיל Java — מבוך ויזואלי מ-API");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 700);
        setLocationRelativeTo(null);

        // --- מסך 1: הגדרות ---
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
        gbc.gridy = 2; gbc.gridx = 0; setupPanel.add(new JLabel("צבע הקירות שנקבע:"), gbc); gbc.gridx = 1; setupPanel.add(wallColorLabel, gbc);
        gbc.gridy = 3; gbc.gridx = 0; setupPanel.add(new JLabel("צבע נתיב הפתרון:"), gbc); gbc.gridx = 1; setupPanel.add(pathColorLabel, gbc);
        gbc.gridy = 4; gbc.gridx = 0; setupPanel.add(new JLabel("האם לצייר קווי רשת:"), gbc); gbc.gridx = 1; setupPanel.add(gridStatusLabel, gbc);
        gbc.gridy = 5; gbc.gridx = 0; setupPanel.add(new JLabel("צבע הרשת:"), gbc); gbc.gridx = 1; setupPanel.add(gridColorLabel, gbc);
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

        JButton btnBack = new JButton("חזור להגדרות");
        btnBack.addActionListener(e -> { if (!isAnimating) cardLayout.show(mainContainer, "SETUP"); });
        gameControlPanel.add(btnBack);

        gamePanel.add(gameControlPanel, BorderLayout.NORTH);
        gamePanel.add(mazePanel, BorderLayout.CENTER);

        mainContainer.add(setupPanel, "SETUP"); mainContainer.add(gamePanel, "GAME");
        add(mainContainer); cardLayout.show(mainContainer, "SETUP");
        setVisible(true);
        fetchConfig();
    }

    private void fetchConfig() {
        new Thread(() -> {
            try {
                String url = "https://backend-qcf9.onrender.com/fm1/get-render-config?t=" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setUseCaches(false);
                c.setRequestProperty("Cache-Control", "no-cache");

                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line); r.close();

                    config.parseJson(sb.toString());

                    SwingUtilities.invokeLater(() -> {
                        wallColorLabel.setText("#" + Integer.toHexString(config.wallCellColor.getRGB()).substring(2).toUpperCase());
                        pathColorLabel.setText("#" + Integer.toHexString(config.pathColor.getRGB()).substring(2).toUpperCase());
                        gridStatusLabel.setText(config.drawGrid ? "כן (True)" : "לא (False)");
                        gridColorLabel.setText("#" + Integer.toHexString(config.gridColor.getRGB()).substring(2).toUpperCase());
                        delayLabel.setText(config.animationDelayMs + " ms");
                    });
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void handleGetMaze() {
        try {
            // קריאת הערכים שרשם המשתמש בשדות הגודל וניקוי רווחים
            mazeWidth = Integer.parseInt(widthField.getText().trim());
            mazeHeight = Integer.parseInt(heightField.getText().trim());

            // משתני עזר שיסמנו לנו האם הייתה חריגה בערכים
            boolean invalidWidth = (mazeWidth < 5 || mazeWidth > 100);
            boolean invalidHeight = (mazeHeight < 5 || mazeHeight > 100);

            // אם יש חריגה, נעדכן גם את המשתנה הלוגי וגם את תיבת הטקסט על המסך ב-Thread של ה-UI
            if (invalidWidth) {
                mazeWidth = 30;
                SwingUtilities.invokeLater(() -> widthField.setText("30"));
            }
            if (invalidHeight) {
                mazeHeight = 30;
                SwingUtilities.invokeLater(() -> heightField.setText("30"));
            }

        } catch (Exception e) {
            // במקרה של קלט שאינו מספר (למשל אותיות), נחזיר את שניהם ל-30 ונעדכן את המסך
            mazeWidth = 30;
            mazeHeight = 30;
            SwingUtilities.invokeLater(() -> {
                widthField.setText("30");
                heightField.setText("30");
            });
        }

        // איפוס נתוני פתרון ואנימציות קודמים לפני טעינת מבוך חדש
        solutionPath.clear();
        animatedPath.clear();

        // פתיחת תהליכון רקע לשליפת ועיבוד התמונה מהאינטרנט
        new Thread(() -> {
            try {
                // בניית כתובת ה-URL הדינמית הכוללת את הגודל המעודכן (30 במקרה של חריגה)
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

                    // החלפת מסכים ועדכון הפאנל נעשים על ה-EDT בצורה בטוחה
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

    public static void main(String[] args) { SwingUtilities.invokeLater(MazeApp::new); }
}