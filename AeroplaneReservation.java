
import java.util.*;

public class AeroplaneReservation {
    public static void main(String[] args) {
        ReservationSystem system = new ReservationSystem();
        system.seedSampleFlights();
        system.runConsole();
    }
}

class Flight {
    final String id;
    final String origin;
    final String destination;
    final int rows;
    final int cols; // seats per row (A,B,C...)
    private final boolean[][] seats; // true = booked

    Flight(String id, String origin, String destination, int rows, int cols) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.rows = rows;
        this.cols = cols;
        this.seats = new boolean[rows][cols];
    }

    synchronized boolean isBooked(int row, int col) {
        return seats[row][col];
    }

    synchronized boolean book(int row, int col) {
        if (seats[row][col]) return false;
        seats[row][col] = true;
        return true;
    }

    synchronized boolean cancel(int row, int col) {
        if (!seats[row][col]) return false;
        seats[row][col] = false;
        return true;
    }

    String seatLabel(int row, int col) {
        return (row + 1) + String.valueOf((char) ('A' + col));
    }

    List<String> availableSeats() {
        List<String> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!seats[r][c]) out.add(seatLabel(r, c));
            }
        }
        return out;
    }

    void printSeatMap() {
        System.out.println("Seat map for flight " + id + " (" + origin + " -> " + destination + ")");
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%2d ", r + 1));
            for (int c = 0; c < cols; c++) {
                sb.append(isBooked(r, c) ? "[X]" : "[" + (char)('A' + c) + "]");
            }
            System.out.println(sb.toString());
        }
    }

    @Override
    public String toString() {
        return id + ": " + origin + " -> " + destination + " (" + rows + "x" + cols + ")";
    }
}

class Reservation {
    final String pnr;
    final String flightId;
    final String passengerName;
    final String seat; // e.g. "3C"

    Reservation(String pnr, String flightId, String passengerName, String seat) {
        this.pnr = pnr;
        this.flightId = flightId;
        this.passengerName = passengerName;
        this.seat = seat;
    }

    @Override
    public String toString() {
        return "PNR: " + pnr + " | Flight: " + flightId + " | Passenger: " + passengerName + " | Seat: " + seat;
    }
}

class ReservationSystem {
    private final Map<String, Flight> flights = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final Random random = new Random();
    private final Scanner scanner = new Scanner(System.in);

    void seedSampleFlights() {
        flights.put("FL100", new Flight("FL100", "New York", "London", 10, 6));
        flights.put("FL200", new Flight("FL200", "San Francisco", "Tokyo", 12, 6));
        flights.put("FL300", new Flight("FL300", "Paris", "Berlin", 8, 4));
    }

    void runConsole() {
        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": listFlights(); break;
                case "2": viewSeatMap(); break;
                case "3": bookSeat(); break;
                case "4": cancelBooking(); break;
                case "5": viewReservation(); break;
                case "0": System.out.println("Exiting."); return;
                default: System.out.println("Invalid option.");
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("=== Aeroplane Reservation System ===");
        System.out.println("1) List flights");
        System.out.println("2) View seat map");
        System.out.println("3) Book seat");
        System.out.println("4) Cancel booking");
        System.out.println("5) View reservation (PNR)");
        System.out.println("0) Exit");
        System.out.print("Choose: ");
    }

    private void listFlights() {
        flights.values().forEach(f -> System.out.println(f));
    }

    private Flight pickFlightPrompt() {
        System.out.print("Enter flight id: ");
        String id = scanner.nextLine().trim();
        Flight f = flights.get(id);
        if (f == null) System.out.println("Flight not found.");
        return f;
    }

    private void viewSeatMap() {
        Flight f = pickFlightPrompt();
        if (f != null) f.printSeatMap();
    }

    private void bookSeat() {
        Flight f = pickFlightPrompt();
        if (f == null) return;
        f.printSeatMap();
        System.out.print("Enter seat (e.g. 3C) or 'auto' for first available: ");
        String seatInput = scanner.nextLine().trim().toUpperCase();
        int row = -1, col = -1;
        if ("AUTO".equals(seatInput)) {
            List<String> avail = f.availableSeats();
            if (avail.isEmpty()) { System.out.println("No seats available."); return; }
            seatInput = avail.get(0);
            System.out.println("Auto-selected seat: " + seatInput);
        }
        try {
            // parse e.g. "3C"
            int len = seatInput.length();
            char last = seatInput.charAt(len - 1);
            col = last - 'A';
            row = Integer.parseInt(seatInput.substring(0, len - 1)) - 1;
            if (row < 0 || row >= f.rows || col < 0 || col >= f.cols) {
                System.out.println("Invalid seat.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Invalid seat format.");
            return;
        }
        synchronized (f) {
            if (!f.book(row, col)) {
                System.out.println("Seat already booked.");
                return;
            }
        }
        System.out.print("Passenger name: ");
        String name = scanner.nextLine().trim();
        String pnr = generatePNR();
        Reservation r = new Reservation(pnr, f.id, name, f.seatLabel(row, col));
        reservations.put(pnr, r);
        System.out.println("Booking successful. " + r);
    }

    private void cancelBooking() {
        System.out.print("Enter PNR to cancel: ");
        String pnr = scanner.nextLine().trim();
        Reservation r = reservations.get(pnr);
        if (r == null) { System.out.println("PNR not found."); return; }
        Flight f = flights.get(r.flightId);
        if (f == null) { System.out.println("Associated flight not found."); return; }
        // parse seat from reservation
        try {
            String seat = r.seat;
            int len = seat.length();
            int row = Integer.parseInt(seat.substring(0, len - 1)) - 1;
            int col = seat.charAt(len - 1) - 'A';
            synchronized (f) {
                if (f.cancel(row, col)) {
                    reservations.remove(pnr);
                    System.out.println("Booking canceled: " + pnr);
                } else {
                    System.out.println("Seat was not booked.");
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing seat in reservation.");
        }
    }

    private void viewReservation() {
        System.out.print("Enter PNR: ");
        String pnr = scanner.nextLine().trim();
        Reservation r = reservations.get(pnr);
        if (r == null) System.out.println("Reservation not found.");
        else System.out.println(r);
    }

    private String generatePNR() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}

