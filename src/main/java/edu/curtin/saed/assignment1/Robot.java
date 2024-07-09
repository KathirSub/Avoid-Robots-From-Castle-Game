package edu.curtin.saed.assignment1;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.Timer;
import java.awt.Point;

public class Robot {
    private static int nextId = 1;
    private int id;
    private double x;
    private double y;
    private long delay;
    private SwingArena arena;
    private Timer moveTimer;
    private boolean isMoving = false;
    private double targetX;
    private double targetY;

    private boolean reachedCitadel = false;

    private final Object robotMutex = new Object();

    // Define the grid size as a constant
    private static final int GRID_WIDTH = 9;
    private static final int GRID_HEIGHT = 9;

    // Randomness for Robot movement
    private static final double RANDOM_MOVE = 0.45; // 45% of a random move

    public Robot(double x, double y, SwingArena arena) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.arena = arena;
        this.delay = generateRandomDelay();

        // Initialize a timer to handle robot movement animation
        moveTimer = new Timer(40, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveAnimationStep();
            }
        });
        moveTimer.setRepeats(true);
        moveTimer.setCoalesce(true);
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public long getDelay() {
        return delay;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean hasReachedCitadel() {
        return reachedCitadel;
    }

    private long generateRandomDelay() {
        return new Random().nextInt(1501) + 500; // Random delay between 500 and 2000 milliseconds
    }

    public boolean canMove() {
        // A robot can move if it hasn't moved within its delay time
        long currentTime = System.currentTimeMillis();
        return currentTime - delay >= 0 && !isMoving;
    }

    public void moveTowardsCitadel() {
        synchronized (robotMutex) {
            if (!isMoving && !reachedCitadel) {
                double randomMoveChance = Math.random();

                if (randomMoveChance < RANDOM_MOVE) { // 45% chance for random move
                    makeRandomMove();
                } else {
                    targetX = x;
                    targetY = y;

                    if (x < GRID_WIDTH / 2 && !isOccupied(x + 1, y)) {
                        targetX++;
                    } else if (x > GRID_WIDTH / 2 && !isOccupied(x - 1, y)) {
                        targetX--;
                    } else if (y < GRID_HEIGHT / 2 && !isOccupied(x, y + 1)) {
                        targetY++;
                    } else if (y > GRID_HEIGHT / 2 && !isOccupied(x, y - 1)) {
                        targetY--;
                    }
                }
                if (x == GRID_WIDTH / 2 && y == GRID_HEIGHT / 2) {
                    reachedCitadel = true;
                    moveTimer.stop();

                    arena.endGame(); // Notify the arena that the game has ended
                }

                // Check if the robot's target position is occupied by a wall
                Point targetLocation = new Point((int) targetX, (int) targetY);
                if (arena.getBuiltWalls().contains(targetLocation)) {
                    // Robot's target position is a wall, remove the robot
                    arena.getRobots().remove(this);
                } else {
                    // Start the move animation if a valid move is possible
                    if (x != targetX || y != targetY && !isOccupied(x, y)) {
                        moveTimer.start();
                        isMoving = true;
                    }
                }

            }
        }

        arena.queueRobotForMovement(this);
    }

    private void makeRandomMove() {
        // Generate a list of possible random moves
        List<Point> possibleMoves = new ArrayList<>();

        if (x > 0 && !isOccupied(x - 1, y)) {
            possibleMoves.add(new Point((int) (x - 1), (int) y));
        }
        if (x < GRID_WIDTH - 1 && !isOccupied(x + 1, y)) {
            possibleMoves.add(new Point((int) (x + 1), (int) y));
        }
        if (y > 0 && !isOccupied(x, y - 1)) {
            possibleMoves.add(new Point((int) x, (int) (y - 1)));
        }
        if (y < GRID_HEIGHT - 1 && !isOccupied(x, y + 1)) {
            possibleMoves.add(new Point((int) x, (int) (y + 1)));
        }

        // Choose a random move from the list
        if (!possibleMoves.isEmpty()) {
            int randomIndex = (int) (Math.random() * possibleMoves.size());
            Point randomMove = possibleMoves.get(randomIndex);
            targetX = randomMove.getX();
            targetY = randomMove.getY();

            // Start the move animation
            moveTimer.start();
            isMoving = true;
        }
    }

    /*
     * This method checks if a location is occupied by another robot.
     * Since it accesses the list of robots, synchronization is necessary.
     */
    private boolean isOccupied(double x, double y) {
        List<Robot> arenaRobots = arena.getRobots();
        synchronized (arenaRobots) {
            for (Robot otherRobot : arenaRobots) {
                if (!otherRobot.equals(this) && Math.floor(otherRobot.getX()) == Math.floor(x)
                        && Math.floor(otherRobot.getY()) == Math.floor(y)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Robot otherRobot = (Robot) obj;
        // Compare unique identifier or properties here
        return Double.compare(otherRobot.getX(), getX()) == 0 && Double.compare(otherRobot.getY(), getY()) == 0;
    }
    @Override
    public int hashCode() {
        int result = 17; // A prime number as an initial value

        // Combine the hash code of relevant fields (in this case, x and y)
        long xBits = Double.doubleToLongBits(x);
        long yBits = Double.doubleToLongBits(y);

        result = 31 * result + (int) (xBits ^ (xBits >>> 32));
        result = 31 * result + (int) (yBits ^ (yBits >>> 32));

        return result;
    }


    /*
     * Method: This method updates the robot's position and delay,
     * which should be synchronized to prevent multiple threads from
     * interfering with the robot's state.
     */
    private void moveAnimationStep() {
        // Calculate the step size based on the target position
        double stepX = (targetX - x > 0) ? 1.0 : (targetX - x < 0) ? -1.0 : 0.0;
        double stepY = (targetY - y > 0) ? 1.0 : (targetY - y < 0) ? -1.0 : 0.0;

        // Move the robot by one grid box at a time
        x += stepX;
        y += stepY;

        // Check if the robot has reached its target
        if (x == targetX && y == targetY) {
            // Stop the move animation
            moveTimer.stop();
            isMoving = false;

            // Update the robot's delay for the next move
            delay = generateRandomDelay();
            arena.robotMoved(this, x, y); // Notify the arena about the movement
        }
    }

}
