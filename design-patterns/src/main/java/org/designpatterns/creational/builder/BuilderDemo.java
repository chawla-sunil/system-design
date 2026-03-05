package org.designpatterns.creational.builder;

public class BuilderDemo {

    public static void run() {
        System.out.println("=== BUILDER PATTERN DEMO ===\n");

        // Simple GET request - only required param
        HttpRequest getRequest = new HttpRequest.Builder("https://api.example.com/users")
                .build();
        System.out.println("GET request:  " + getRequest);

        // POST request with all options
        HttpRequest postRequest = new HttpRequest.Builder("https://api.example.com/users")
                .method("POST")
                .body("{\"name\": \"John\", \"email\": \"john@example.com\"}")
                .contentType("application/json")
                .authorization("Bearer token123")
                .timeout(5000)
                .followRedirects(false)
                .build();
        System.out.println("POST request: " + postRequest);

        // DELETE request
        HttpRequest deleteRequest = new HttpRequest.Builder("https://api.example.com/users/42")
                .method("DELETE")
                .authorization("Bearer admin-token")
                .timeout(10000)
                .build();
        System.out.println("DEL request:  " + deleteRequest);

        System.out.println();
    }
}
