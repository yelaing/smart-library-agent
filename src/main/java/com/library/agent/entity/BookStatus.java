package com.library.agent.entity;

public enum BookStatus {
    AVAILABLE("在馆"),
    BORROWED("已借出");

    private final String label;

    BookStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
