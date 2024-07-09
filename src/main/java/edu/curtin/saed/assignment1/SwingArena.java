package edu.curtin.saed.assignment1;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import java.util.List; // So that 'List' means java.util.List and not java.awt.List.
import java.util.Timer;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ArrayBlockingQueue; // Import the ArrayBlockingQueue
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A Swing GUI element that displays a grid on which you can draw images, text
 * and lines.
 */
public class SwingArena extends JPanel {
    // Represents the image to draw. You can modify this to introduce multiple
    // images.
    private static final String IMAGE_FILE = "1554047213.png";
    private static final String COMPLETE_WALL_FILE = "181478.png";
    private static final String BROKEN_WALL_FILE = "181479.png";
    private static final String CITADEL_FILE = "rg1024-isometric-tower.png";

    private ImageIcon robot1, completeWall, brokenWall, citadel;

    private int gridWidth = 9;
    private int gridHeight = 9;

    private double gridSquareSize;

    private List<ArenaListener> listeners = null;

    // Defineblocking queues for different types of tasks
    private ArrayBlockingQueue<Robot> movementQueue; // Declare a blocking queue for robot movements
    private BlockingQueue<WallRequest> wallRequestQueue = new LinkedBlockingQueue<>();

    //Define ThreadPools
    private ExecutorService threadPool;
    private ExecutorService robotanimationES;// Thread pool for animating movements of robots

    private final Object arenaMutex = new Object();

    // needed to end threads and the game in general
    private boolean gameOver = false;

    // Initializer
    private List<Robot> robots = new CopyOnWriteArrayList<>();
    private Timer robotSpawnTimer;
    private Timer robotMoveTimer;
    private Timer wallBuildTimer;
    private Thread scoreTimer;
    private int wallCooldown = 2000; //2000ms cooldown between placing walls
    private Random random = new Random();

    private List<Point> wallQueue = new ArrayList<>(); // Queue for wall-building commands
    private List<Point> builtWalls = new ArrayList<>(); // List of built walls
    private int maxWalls = 10; // Maximum number of walls that can be built
    private int wallNumber = 0;

    private List<Point> completeWalls = new ArrayList<>(); // List of complete walls
    private List<Point> brokenWalls = new ArrayList<>(); // List of broken walls

    private JTextArea messageTextArea;
    public JLabel wallLabel;

    /**
     * Creates a new arena object, loading the robot image.
     */
    public SwingArena(JTextArea messageTextArea, JLabel label, JLabel wallLabel) {
        // Initializing the blocking queue
        movementQueue = new ArrayBlockingQueue<>(500);
        this.messageTextArea = messageTextArea;
        this.wallLabel = wallLabel;

        threadPool = Executors.newFixedThreadPool(4);
        robotanimationES = Executors.newFixedThreadPool(2);

        // Here's how (in Swing) you get an Image object from an image file that's part
        // of the
        // project's "resources". If you need multiple different images, you can modify
        // this code
        // accordingly.

        // (NOTE: _DO NOT_ use ordinary file-reading operations here, and in particular
        // do not try
        // to specify the file's path/location. That will ruin things if you try to
        // create a
        // distributable version of your code with './gradlew build'. The approach below
        // is how a
        // project is supposed to read its own internal resources, and should work both
        // for
        // './gradlew run' and './gradlew build'.)

        URL robot1Url = getClass().getClassLoader().getResource(IMAGE_FILE);
        if (robot1Url == null) {
            throw new AssertionError("Cannot find image file" + IMAGE_FILE);
        }
        robot1 = new ImageIcon(robot1Url);

        // Load the Citadel image
        URL citadelUrl = getClass().getClassLoader().getResource(CITADEL_FILE);
        if (citadelUrl == null) {
            throw new AssertionError("Cannot find image file" + CITADEL_FILE);
        }
        citadel = new ImageIcon(citadelUrl);

        // Load the BrokenWall image
        URL brokenWallUrl = getClass().getClassLoader().getResource(BROKEN_WALL_FILE);
        if (brokenWallUrl == null) {
            throw new AssertionError("Cannot find image file" + BROKEN_WALL_FILE);
        }
        brokenWall = new ImageIcon(brokenWallUrl);

        // Load the CompleteWall image
        URL completeWallUrl = getClass().getClassLoader().getResource(COMPLETE_WALL_FILE);
        if (completeWallUrl == null) {
            throw new AssertionError("Cannot find image file" + COMPLETE_WALL_FILE);
        }
        completeWall = new ImageIcon(completeWallUrl);

        // Create the ScoreManager instance
        ScoreManager scoreManager = ScoreManager.getInstance();

        Runnable score = new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    synchronized (arenaMutex) {
                        if (!gameOver) {
                            // Increment the score by time using ScoreManager
                            scoreManager.incrementScoreByTime();
                            // Get the updated score from ScoreManager
                            int score = scoreManager.getScore();
                            SwingUtilities.invokeLater(() -> label.setText("Score: " + score + " "));
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        scoreTimer = new Thread(score, "Score Thread");
        scoreTimer.start();

       // Start the timer to spawn robots every 1500 milliseconds
        robotSpawnTimer = new Timer();
        robotSpawnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                spawnRobot();
            }
        }, 0, 1500);

        robotMoveTimer = new Timer();
        robotMoveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                moveRobots();
            }
        }, 0, 1000); // Adjust the interval (1000 milliseconds = 1 second) as needed

        wallBuildTimer = new Timer();
        wallBuildTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                buildWallFromQueue(); // Build walls from the queue
            }
        }, 0, wallCooldown);

       
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int gridX = (int) ((double) event.getX() / gridSquareSize);
                int gridY = (int) ((double) event.getY() / gridSquareSize);

                if (gridX < gridWidth && gridY < gridHeight) {
                    Point location = new Point(gridX, gridY);
                    buildWall(location, wallLabel); // Add the wall-building command to the queue
                }
            }
        });

    }
    public void requestBuildWall(WallRequest request) {
        try {
            wallRequestQueue.put(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Wall request Interrupted" + e.getMessage());
        }
    }

    public WallRequest getNextWallRequest() {
        try {
            return wallRequestQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error taking wall request: " + e.getMessage());
            return null;
        }
    }

    public List<Robot> getRobots() {
        synchronized (arenaMutex) {
            return robots;
        }
    }

    public List<Point> getBuiltWalls() {
        synchronized (arenaMutex) {
            return builtWalls;
        }
    }

    public void buildWall(Point location, JLabel wallLabel) {
        // Check if the player has already queued up the maximum number of walls
        if (wallNumber < maxWalls) {
            wallNumber++;
            // Add the wall-building command to the queue
            wallQueue.add(location);
            SwingUtilities.invokeLater(() -> wallLabel.setText(" Queue: " + wallNumber));

            SwingUtilities.invokeLater(() -> repaint());
        }
    }

    private void buildWallFromQueue() {
        if (!gameOver) {
            synchronized (arenaMutex) {
                if (!wallQueue.isEmpty()) {
                    Iterator<Point> iterator = wallQueue.iterator();
                    while (iterator.hasNext()) {
                        Point location = iterator.next();

                        // Check if the square is unoccupied (no robot)
                        if (!isSquareOccupied(location)) {
                            // Add the built wall to the list
                            completeWalls.add(location);
                            messageTextArea
                                    .append("Wall built at (" + location.getX() + ", " + location.getY() + ").\n");

                            // Remove the processed wall-building command
                            iterator.remove();

                            SwingUtilities.invokeLater(() -> repaint());
                        }
                    }
                }
            }

        }
    }

    private boolean isSquareOccupied(Point location) {
        // Check if there's a robot at the specified location
        for (Robot robot : robots) {
            if (robot.getX() == location.getX() && robot.getY() == location.getY()) {
                return true;
            }
        }
        return false;
    }

    // Method to spawn a new robot at a random corner of the grid

    private void spawnRobot() {

        int corner;
        double startX, startY;

        if (gameOver == false) {
            // Keep generating a random corner until an empty one is found
            do {
                corner = random.nextInt(4); // Randomly select a corner (0-3)

                switch (corner) {
                    case 0:
                        startX = 0;
                        startY = 0;
                        break;
                    case 1:
                        startX = gridWidth - 1;
                        startY = 0;
                        break;
                    case 2:
                        startX = 0;
                        startY = gridHeight - 1;
                        break;
                    default:
                        startX = gridWidth - 1;
                        startY = gridHeight - 1;
                        break;
                }
            } while (checkCorner(startX, startY)); // Check if a robot already occupies the corner

            Robot newRobot = new Robot(startX, startY, this);
            robots.add(newRobot);
            messageTextArea.append("Robot #" + newRobot.getId() + " spawned.\n");
        }

        SwingUtilities.invokeLater(() -> repaint());

    }

    // to check if a corner is already occupied
    private boolean checkCorner(double x, double y) {
        for (Robot robot : robots) {
            if (Math.abs(robot.getX() - x) < 0.1 && Math.abs(robot.getY() - y) < 0.1) {
                return true;
            }
        }
        return false;
    }

    private boolean isRobotCollision(double newX, double newY, Robot movingRobot) {
        for (Robot robot : robots) {
            if (!robot.equals(movingRobot) && (Math.abs(robot.getX() - newX) < 0.5)
                    && (Math.abs(robot.getY() - newY) < 0.5)) {
                return true; // Collision detected
            }
        }
        return false; // No collision
    }

    public void robotMoved(Robot robot, double newX, double newY) {
        if (!gameOver) {

            // Check for collisions and update robot position
            boolean collision = false;
            for (Robot otherRobot : robots) {
                if (!otherRobot.equals(robot) && (Math.abs(otherRobot.getX() - newX) < 0.5)
                        && (Math.abs(otherRobot.getY() - newY) < 0.5)) {
                    collision = true;
                    break;
                }
            }

            if (collision) {
                // Robot collided with another robot, remove it
                robots.remove(robot);
                SwingUtilities.invokeLater(() -> repaint());

            } else {

                /* */
                // Check if the robot has moved onto a wall
                Point robotLocation = new Point((int) robot.getX(), (int) robot.getY());
                if (completeWalls.contains(robotLocation)) {
                    // Robot moved onto a complete wall, remove the robot, replace the wall with a
                    // broken wall
                    robots.remove(robot);
                    wallReplace(robotLocation);
                } else if (brokenWalls.contains(robotLocation)) {
                    // Robot moved onto a broken wall, remove both the robot and the broken wall
                    robots.remove(robot);
                    brokenWalls.remove(robotLocation);
                    messageTextArea
                            .append("Wall impacted at (" + robotLocation.getX() + ", " + robotLocation.getY() + ").\n");
                    wallNumber--;

                    // Update the score when a wall is impacted
                    ScoreManager.getInstance().incrementScoreByWallImpact();
                } else {
                    // Robot didn't collide with a wall, update its position
                    robot.setX(newX);
                    robot.setY(newY);
                }
            }
            SwingUtilities.invokeLater(() -> repaint());
        }
    }

    // New method to queue movement request for a robot
    public void queueRobotForMovement(Robot robot) {
        if (!gameOver) {

            try {
                movementQueue.put(robot); // Add the robot to the blocking queue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                System.err.println("Robot movement interrupted: " + e.getMessage());
            }
        }
    }

    /**
     * Move robots and update their positions.
     */

    private void moveRobots() {
        if (!gameOver) {
            synchronized (arenaMutex) {
                for (Robot robot : robots) {
                    if (robot.canMove()) {
                        // Check if the thread pool is not shutting down
                        if (!threadPool.isShutdown()) {
                            threadPool.execute(() -> {
                                synchronized (arenaMutex) {

                                    if (!contactsWall(robot.getX(), robot.getY())
                                            && !isRobotCollision(robot.getX(), robot.getY(), robot)) {
                                        // Check if the robot can move to the new position without
                                        // colliding with a wall or another robot
                                        robot.moveTowardsCitadel();
                                    }
                                    SwingUtilities.invokeLater(() -> repaint());
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private boolean contactsWall(double x, double y) {

        // Check if the robot contacts any type of wall (complete or broken)
        Point location = new Point((int) x, (int) y);
        return builtWalls.contains(location);
    }

    private void wallReplace(Point location) {
        if (!gameOver) {
            synchronized (arenaMutex) {
                // Remove the built wall (complete wall) at the specified location
                if (completeWalls.contains(location)) {
                    completeWalls.remove(location);
                    // Add a broken wall at the same location
                    brokenWalls.add(location);
                    messageTextArea.append("Wall impacted at (" + location.getX() + ", " + location.getY() + ").\n");

                    // Update the score when a wall is impacted
                    ScoreManager.getInstance().incrementScoreByWallImpact();

                    SwingUtilities.invokeLater(() -> repaint());
                }
            }
        }
    }

     /**
     * Ends the game, cleans up resources, and shuts down threads.
     */
    
    public void endGame() {
        ScoreManager scoreManager = ScoreManager.getInstance();
        gameOver = true; // Set the game over flag
        messageTextArea.append("Game Over, Your Score :\n" + scoreManager.getScore());
        scoreTimer.interrupt();
        builtWalls.clear();
        completeWalls.clear();
        brokenWalls.clear();
        robots.clear();

        // Gracefully shut down the thread pools
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    // If threads don't terminate within 5 seconds, forcefully shut them down
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("Error when closing Thread" + e.getMessage());
            }
        }
        if (robotanimationES != null) {
            robotanimationES.shutdown();
            try {
                if (!robotanimationES.awaitTermination(5, TimeUnit.SECONDS)) {
                    // If threads don't terminate within 5 seconds, forcefully shut them down
                    robotanimationES.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("Error when closing Thread" + e.getMessage());
            }
        }
        if (robotSpawnTimer != null) {
            robotSpawnTimer.cancel();
        }

        if (robotMoveTimer != null) {
            robotMoveTimer.cancel();
        }

        if (wallBuildTimer != null) {
            wallBuildTimer.cancel();
        }

    }

    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * Adds a callback for when the user clicks on a grid square within the arena.
     * The callback
     * (of type ArenaListener) receives the grid (x,y) coordinates as parameters to
     * the
     * 'squareClicked()' method.
     */
    public void addListener(ArenaListener newListener) {
        if (listeners == null) {
            listeners = new LinkedList<>();
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int gridX = (int) ((double) event.getX() / gridSquareSize);
                    int gridY = (int) ((double) event.getY() / gridSquareSize);

                    if (gridX < gridWidth && gridY < gridHeight) {
                        for (ArenaListener listener : listeners) {
                            listener.squareClicked(gridX, gridY);
                        }
                    }
                }
            });
        }
        listeners.add(newListener);
    }

    /**
     * This method is called in order to redraw the screen, either because the user
     * is manipulating
     * the window, OR because you've called 'repaint()'.
     *
     * You will need to modify the last part of this method; specifically the
     * sequence of calls to
     * the other 'draw...()' methods. You shouldn't need to modify anything else
     * about it.
     */
    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D) g;
        gfx.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // First, calculate how big each grid cell should be, in pixels. (We do need to
        // do this
        // every time we repaint the arena, because the size can change.)
        gridSquareSize = Math.min(
                (double) getWidth() / (double) gridWidth,
                (double) getHeight() / (double) gridHeight);

        int arenaPixelWidth = (int) ((double) gridWidth * gridSquareSize);
        int arenaPixelHeight = (int) ((double) gridHeight * gridSquareSize);

       
        // Draw the arena grid lines. This may help for debugging purposes, and just
        // generally
        // to see what's going on.
        gfx.setColor(Color.GRAY);
        gfx.drawRect(0, 0, arenaPixelWidth - 1, arenaPixelHeight - 1); // Outer edge

        for (int gridX = 1; gridX < gridWidth; gridX++) // Internal vertical grid lines
        {
            int x = (int) ((double) gridX * gridSquareSize);
            gfx.drawLine(x, 0, x, arenaPixelHeight);
        }

        for (int gridY = 1; gridY < gridHeight; gridY++) // Internal horizontal grid lines
        {
            int y = (int) ((double) gridY * gridSquareSize);
            gfx.drawLine(0, y, arenaPixelWidth, y);
        }

        
         // Draw the walls

         //we synchronize to ensure that only one thread can build walls and robots 
         synchronized(arenaMutex) {
            for (Point wall : completeWalls) {
                drawImage(gfx, completeWall, wall.getX(), wall.getY());
            }
            for (Point wall : brokenWalls) {
                drawImage(gfx, brokenWall, wall.getX(), wall.getY());
            }
        }

        synchronized(arenaMutex) {
            for (Robot robot : robots) {
                drawImage(gfx, robot1, robot.getX(), robot.getY());
                drawLabel(gfx, String.valueOf(robot.getId()), robot.getX(), robot.getY());
            }
        }

        // draws Citadel position
        drawImage(gfx, citadel, 4.0, 4.0);

    }

    /**
     * Draw an image in a specific grid location. *Only* call this from within
     * paintComponent().
     *
     * Note that the grid location can be fractional, so that (for instance), you
     * can draw an image
     * at location (3.5,4), and it will appear on the boundary between grid cells
     * (3,4) and (4,4).
     * 
     * You shouldn't need to modify this method.
     */
    private void drawImage(Graphics2D gfx, ImageIcon icon, double gridX, double gridY) {
        // Get the pixel coordinates representing the centre of where the image is to be
        // drawn.
        double x = (gridX + 0.5) * gridSquareSize;
        double y = (gridY + 0.5) * gridSquareSize;

        // We also need to know how "big" to make the image. The image file has a
        // natural width
        // and height, but that's not necessarily the size we want to draw it on the
        // screen. We
        // do, however, want to preserve its aspect ratio.
        double fullSizePixelWidth = (double) robot1.getIconWidth();
        double fullSizePixelHeight = (double) robot1.getIconHeight();

        double displayedPixelWidth, displayedPixelHeight;
        if (fullSizePixelWidth > fullSizePixelHeight) {
            // Here, the image is wider than it is high, so we'll display it such that it's
            // as
            // wide as a full grid cell, and the height will be set to preserve the aspect
            // ratio.
            displayedPixelWidth = gridSquareSize;
            displayedPixelHeight = gridSquareSize * fullSizePixelHeight / fullSizePixelWidth;
        } else {
            // Otherwise, it's the other way around -- full height, and width is set to
            // preserve the aspect ratio.
            displayedPixelHeight = gridSquareSize;
            displayedPixelWidth = gridSquareSize * fullSizePixelWidth / fullSizePixelHeight;
        }

        // Actually put the image on the screen.
        gfx.drawImage(icon.getImage(),
                (int) (x - displayedPixelWidth / 2.0), // Top-left pixel coordinates.
                (int) (y - displayedPixelHeight / 2.0),
                (int) displayedPixelWidth, // Size of displayed image.
                (int) displayedPixelHeight,
                null);
    }

    /**
     * Displays a string of text underneath a specific grid location. *Only* call
     * this from within
     * paintComponent().
     *
     * You shouldn't need to modify this method.
     */
    private void drawLabel(Graphics2D gfx, String label, double gridX, double gridY) {
        gfx.setColor(Color.BLUE);
        FontMetrics fm = gfx.getFontMetrics();
        gfx.drawString(label,
                (int) ((gridX + 0.5) * gridSquareSize - (double) fm.stringWidth(label) / 2.0),
                (int) ((gridY + 1.0) * gridSquareSize) + fm.getHeight());
    }

}