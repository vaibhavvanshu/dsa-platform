package com.capstone.dsaplatform.domain;

public enum Difficulty {
    EASY(1),
    MEDIUM(2),
    HARD(3);

    // Numeric weight so topic_state.mean_difficulty can be an average (an HLR feature).
    private final int weight;

    Difficulty(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
