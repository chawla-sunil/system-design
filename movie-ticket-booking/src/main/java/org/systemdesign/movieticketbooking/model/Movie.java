package org.systemdesign.movieticketbooking.model;

import java.time.Duration;
import java.util.UUID;

public class Movie {
    private final String id;
    private final String title;
    private final Duration duration;
    private final String genre;
    private final String language;

    public Movie(String title, Duration duration, String genre, String language) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.duration = duration;
        this.genre = genre;
        this.language = language;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Duration getDuration() { return duration; }
    public String getGenre() { return genre; }
    public String getLanguage() { return language; }

    @Override
    public String toString() {
        return "Movie{title='" + title + "', genre='" + genre + "', language='" + language +
                "', duration=" + duration.toMinutes() + "min}";
    }
}

