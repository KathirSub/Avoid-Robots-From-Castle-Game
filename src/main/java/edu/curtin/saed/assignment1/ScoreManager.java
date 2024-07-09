package edu.curtin.saed.assignment1;

public class ScoreManager {
    private int score;

    // Private constructor to prevent instantiation
    private ScoreManager() {
        score = 0;
    }

    private static class ScoreManagerHolder {
        private static final ScoreManager INSTANCE = new ScoreManager();
    }

    public static ScoreManager getInstance() {
        return ScoreManagerHolder.INSTANCE;
    }

    public int getScore() {
        return score;
    }

    public void incrementScoreByWallImpact() {
        score += 100;
    }

    public void incrementScoreByTime() {
        score += 10;
    }

    public void resetScore() {
        score = 0;
    }
}
