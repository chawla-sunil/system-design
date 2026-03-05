package org.designpatterns.creational.prototype;

public class Spreadsheet extends Document {

    private int rows;
    private int columns;

    public Spreadsheet(String title, String content, String author, int rows, int columns) {
        super(title, content, author);
        this.rows = rows;
        this.columns = columns;
    }

    private Spreadsheet(Spreadsheet source) {
        super(source);
        this.rows = source.rows;
        this.columns = source.columns;
    }

    @Override
    public Document cloneDocument() {
        System.out.println("  [Clone] Cloning spreadsheet (no expensive init)");
        return new Spreadsheet(this);
    }

    public int getRows() { return rows; }
    public int getColumns() { return columns; }
}
