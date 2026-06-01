package com.library.agent.exception;

public class BookNotFoundException extends RuntimeException {

    private final String isbn;

    public BookNotFoundException(String isbn) {
        super("未找到ISBN为 " + isbn + " 的图书");
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
