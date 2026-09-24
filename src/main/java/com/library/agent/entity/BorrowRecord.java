package com.library.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 借阅记录。
 *
 * <p>复合索引服务于还书时的查询
 * {@code findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc}：
 * 该查询按 book_id 等值过滤、再按 return_date 判空，复合索引比 MySQL 为外键
 * 自动创建的单列 book_id 索引更有选择性。</p>
 */
@Entity
@Table(name = "borrow_records", indexes = {
        @Index(name = "idx_borrow_records_book_return", columnList = "book_id, return_date")
})
public class BorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "borrower_name", nullable = false, length = 50)
    private String borrowerName;

    @Column(name = "borrow_date", nullable = false)
    private LocalDateTime borrowDate;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public BorrowRecord() {}

    public BorrowRecord(Book book, String borrowerName) {
        this.book = book;
        this.borrowerName = borrowerName;
        this.borrowDate = LocalDateTime.now();
        this.dueDate = this.borrowDate.plusDays(30);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public String getBorrowerName() { return borrowerName; }
    public void setBorrowerName(String borrowerName) { this.borrowerName = borrowerName; }
    public LocalDateTime getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDateTime borrowDate) { this.borrowDate = borrowDate; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
