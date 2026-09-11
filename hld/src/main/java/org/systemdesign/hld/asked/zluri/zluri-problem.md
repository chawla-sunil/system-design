# Railway Reservation Platform — Problem Statement

## 1. Scenario

You are designing the core of a railway reservation platform similar to IRCTC.

- Trains run on fixed routes that pass through an ordered sequence of stations.
- A single train can carry many passengers, each travelling between a different pair of stations along that route, in one of several travel classes (e.g. Sleeper, 3A, 2A, 1A).

We will focus on **two capabilities**. Design the system so these work correctly and at scale; everything else is secondary.

## 2. Requirements

### 2.1 Mandatory

1. **Search available trains.** Given a source station, a destination station, and a journey date, return the trains that run between those two stations on that date, along with the seat availability and fare for each travel class.
2. **Confirm a ticket.** For a selected train, date, and class, a user can book a ticket for one or more passengers. On success the system issues a confirmed ticket (a PNR) with allocated seats; the booked seats must not be sold to anyone else.
