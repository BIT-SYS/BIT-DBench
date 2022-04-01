
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Scanner;

public class GamePanel extends JPanel implements ActionListener {

    static final int SCREEN_ANCHO = 600;
    static final int SCREEN_ALTO = 600;
    static final int UNIT_SIZE = 25;
    static final int GAME_UNITS = ((SCREEN_ANCHO * SCREEN_ALTO) / UNIT_SIZE);
    static final int DELAY = 50;

    final int[] x = new int[GAME_UNITS];
    final int[] y = new int[GAME_UNITS];

    int bodyParts = 6;
    int applesEaten;
    int maxScore = 0;
    int appleX;
    int appleY;
    char direction = 'R';
    boolean running = false;
    Timer timer;   // crea un objeto de tipo timer que sirve para iniciar acciones cada x tiempo
    Random random; // objeto de tipo random para generar numeros (posiciones) aleatorias


    GamePanel() {
        random = new Random();
        this.setPreferredSize(new Dimension(SCREEN_ANCHO, SCREEN_ALTO));
        this.setBackground(Color.BLACK);
        this.setFocusable(true); //hace que que los eventos incidan sobre el panel (hace que sea posible ponerle el foco)
        this.addKeyListener(new MyKeyAdapter());
        startGame();
    }

    public void startGame() {
        newApple();
        running = true;
        timer = new Timer(DELAY, this); //contador que actua cada x tiempo sobre el objeto indicado (el panel si pones this)
        timer.start(); //comienza el contador

    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g) {
        if (running) {
            g.setColor(Color.RED);
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

            for (int i = 0; i < bodyParts; i++) {
                if (i == 0) {
                    g.setColor(Color.green.darker().darker().darker());
                    g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
                } else {
                    g.setColor(Color.green.brighter());
                    g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)).brighter());
                    g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
                }
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 35));
                FontMetrics metrics = getFontMetrics(g.getFont());
                g.drawString("Score: " + applesEaten, (SCREEN_ANCHO - metrics.stringWidth("Score: " + applesEaten)) / 2, g.getFont().getSize());
                g.drawLine(0, 50, SCREEN_ANCHO, 50);
            }
        } else {
            gameOver(g);
        }

    }

    public void newApple() {
        appleX = random.nextInt((int) (SCREEN_ANCHO / UNIT_SIZE)) * UNIT_SIZE;
        appleY = random.nextInt((int) ((SCREEN_ALTO - 50) / UNIT_SIZE)) * UNIT_SIZE + 50;
    }

    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }

        switch (direction) {
            case 'U':
                y[0] = y[0] - UNIT_SIZE;
                break;
            case 'D':
                y[0] = y[0] + UNIT_SIZE;
                break;
            case 'L':
                x[0] = x[0] - UNIT_SIZE;
                break;
            case 'R':
                x[0] = x[0] + UNIT_SIZE;
                break;
        }
    }

    public void checkApple() {
        if ((x[0] == appleX) && (y[0] == appleY)) {
            bodyParts++;
            applesEaten++;
            newApple();
        }
    }

    public void checkCollisions() {
        //revisa si la cabeza se choca con el cuerpo
        for (int i = bodyParts; i > 0; i--) {
            if ((x[0] == x[i]) && (y[0] == y[i])) {
                running = false;
            }
        }
        //revisa si la cabeza toca borde izquierdo
        if (x[0] < 0) {
            x[0] = SCREEN_ANCHO - Math.abs(x[0]);
        }
        //revisa si la cabeza toca borde derecho
        if (x[0] >= SCREEN_ANCHO) {
            x[0] = x[0] % SCREEN_ANCHO;
        }
        //revisa si la cabeza toca borde superior
        if (y[0] < 50) {
            y[0] = SCREEN_ALTO - Math.abs(y[0]);
        }
        //revisa si la cabeza toca borde inferior
        if (y[0] >= SCREEN_ALTO) {
            y[0] = y[0] % (SCREEN_ALTO - 50);
        }
    }


    public void gameOver(Graphics g) {
        //GAMEOVER TEXT
        g.setColor(Color.RED.brighter());
        g.setFont(new Font("Arial", Font.BOLD, 75));
        FontMetrics metrics = getFontMetrics(g.getFont());
        g.drawString("GAME OVER", (SCREEN_ANCHO - metrics.stringWidth("GAME OVER")) / 2, SCREEN_ALTO / 2 - 20);

        //SCORE
        g.setFont(new Font("Arial", Font.BOLD, 55));
        metrics = getFontMetrics(g.getFont());
        g.drawString("Score: " + applesEaten, (SCREEN_ANCHO - metrics.stringWidth("Score: " + applesEaten)) / 2, SCREEN_ALTO / 2 + 130);

        //PLAY AGAIN
        g.setFont(new Font("Arial", Font.BOLD, 32));
        metrics = getFontMetrics(g.getFont());
        g.drawString("Press SPACE BAR to play again", (SCREEN_ANCHO - metrics.stringWidth("Press SPACE BAR to playa again")) / 2, SCREEN_ALTO / 2 + 40);

        saveMaxScore();

        try {
            Scanner maxScoreReader = new Scanner(new File(getUsersProjectRootDirectoryForSavingMaxScore()));

            if (maxScoreReader.hasNext()) {
                int savedNumber = maxScoreReader.nextInt();
                maxScore = savedNumber > applesEaten ? savedNumber : applesEaten;
            } else {
                maxScore = applesEaten;
            }

        } catch (IOException e) {
            System.out.println(e.getStackTrace());
        }

        g.drawString("MAX SCORE: " + maxScore, (SCREEN_ANCHO - metrics.stringWidth("MAX SCORE: " + maxScore)) / 2, SCREEN_ALTO / 2 - 150 );


    }

    private void saveMaxScore() {
        File root = new File(getUsersProjectRootDirectoryForSavingMaxScore());

        try (FileWriter fileWriter = new FileWriter(root);
             Scanner reader = new Scanner(root)) {
            if (root.createNewFile()) {
                fileWriter.write(Integer.toString(applesEaten));
            } else if(reader.hasNextInt() && applesEaten > reader.nextInt()){
                fileWriter.write(Integer.toString(applesEaten));
            } else{
                RandomAccessFile cleaner = new RandomAccessFile(root, "rw");
                cleaner.setLength(0);
                fileWriter.write(Integer.toString(applesEaten));
            }
        } catch (IOException e) {
            System.out.println("Error creating file");
        }
    }

    public String getUsersProjectRootDirectoryForSavingMaxScore() {
        String separator = System.getProperty("file.separator");
        String envRootDir = System.getProperty("user.dir");
        Path rootDir = Paths.get(".").normalize().toAbsolutePath();
        if (rootDir.startsWith(envRootDir)) {
            return rootDir+ separator + "maxScore.txt";
        } else {
            throw new RuntimeException("Root dir not found in user directory.");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running) {
            move();
            checkApple();
            checkCollisions();
        }
        repaint();
    }

    public class MyKeyAdapter extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') {
                        direction = 'L';
                    }
                    break;
                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') {
                        direction = 'R';
                    }
                    break;
                case KeyEvent.VK_UP:
                    if (direction != 'D') {
                        direction = 'U';
                    }
                    break;
                case KeyEvent.VK_DOWN:
                    if (direction != 'U') {
                        direction = 'D';
                    }
                    break;
            }
            if (e.getKeyCode() == KeyEvent.VK_SPACE) { //this is were i tried to close the old window
                SnakeGame.currentFrame.dispose();
                SnakeGame.currentFrame = new GameFrame();
            }
        }
    }
}


