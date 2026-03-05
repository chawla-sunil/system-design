package org.designpatterns.creational.prototype;

public class Report extends Document {

    private String reportType;

    public Report(String title, String content, String author, String reportType) {
        super(title, content, author);
        this.reportType = reportType;
    }

    private Report(Report source) {
        super(source);
        this.reportType = source.reportType;
    }

    @Override
    public Document cloneDocument() {
        System.out.println("  [Clone] Cloning report (no expensive init)");
        return new Report(this);
    }

    public String getReportType() { return reportType; }
}
