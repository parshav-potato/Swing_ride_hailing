import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;
import javax.swing.Timer; // Add this import at the top of your file


public class CabSharingSystem extends JFrame {
    private Map<String, Trip> tripMap = new ConcurrentHashMap<>(); // Add this line
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JPanel passengerWaitingPanel;
    private JPanel hostTripPanel;


    private static final String USER_FILE = "users.txt";
    private static final String TRIP_FILE = "trips.txt";
    
    private Map<String, User> users = new ConcurrentHashMap<>();
    private List<Trip> trips = new CopyOnWriteArrayList<>();
    private User currentUser;

   


    private Set<Trip> displayedTrips = new HashSet<>();

    private DefaultListModel<Trip> listModel = new DefaultListModel<>();
    private JList<Trip> tripList;

    public CabSharingSystem() {
        setTitle("Cab Sharing System");
        setSize(700, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        loadUsers();
        loadTrips();

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        createLoginPage();
        createSignUpPage();
        createMainPage();
        createPassengerWaitingPanel();
        createHostTripPanel();

        add(cardPanel);
        setVisible(true);

        // Start a thread to update trip information
        new Thread(this::updateTripInformation).start();
    }

    
    private void createPassengerWaitingPanel() {
        passengerWaitingPanel = new JPanel(new BorderLayout());
        JTextArea tripInfoArea = new JTextArea();
        tripInfoArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(tripInfoArea);
        passengerWaitingPanel.add(scrollPane, BorderLayout.CENTER);

        JButton backButton = new JButton("Back to Search");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, "MainPage"));
        passengerWaitingPanel.add(backButton, BorderLayout.SOUTH);

        cardPanel.add(passengerWaitingPanel, "PassengerWaiting");
    }

    private void createHostTripPanel() {
        hostTripPanel = new JPanel(new BorderLayout());
        JTextArea participantsArea = new JTextArea();
        participantsArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(participantsArea);
        hostTripPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton beginTripButton = new JButton("Begin Trip");
        JButton backButton = new JButton("Back to Main");
        buttonPanel.add(beginTripButton);
        buttonPanel.add(backButton);
        hostTripPanel.add(buttonPanel, BorderLayout.SOUTH);

        backButton.addActionListener(e -> cardLayout.show(cardPanel, "MainPage"));

        cardPanel.add(hostTripPanel, "HostTrip");
    }

    private void showPassengerWaitingScreen(Trip trip) {
        JTextArea tripInfoArea = findTextArea(passengerWaitingPanel);
        updatePassengerWaitingInfo(tripInfoArea, trip);

        // Start a timer to update the waiting screen periodically
        Timer timer = new Timer(5000, e -> updatePassengerWaitingInfo(tripInfoArea, trip));
        timer.start();
        tripInfoArea.putClientProperty("timer", timer);

        cardLayout.show(cardPanel, "PassengerWaiting");
    }

    private void updatePassengerWaitingInfo(JTextArea tripInfoArea, Trip trip) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        StringBuilder info = new StringBuilder();
        info.append("Trip Number: ").append(trip.id).append("\n");
        info.append("Status: ").append(trip.isStarted ? "Started" : "Waiting").append("\n");
        info.append("Host: ").append(trip.host.name).append("\n");
        info.append("Host Phone: ").append(trip.host.phone).append("\n");
        info.append("From: ").append(trip.origin).append("\n");
        info.append("To: ").append(trip.destination).append("\n");
        info.append("Departure: ").append(sdf.format(trip.departureTime)).append("\n");
        info.append("Available Seats: ").append(trip.getAvailableSeats()).append("\n");

        tripInfoArea.setText(info.toString());

        if (trip.isStarted) {
            ((Timer) tripInfoArea.getClientProperty("timer")).stop();
            JOptionPane.showMessageDialog(this, "Your trip has started!");
            cardLayout.show(cardPanel, "MainPage");
        }
    }

    private void showHostTripScreen(Trip trip) {
        JTextArea participantsArea = findTextArea(hostTripPanel);
        JButton beginTripButton = findButton(hostTripPanel, "Begin Trip");

        updateHostTripInfo(participantsArea, trip);

        beginTripButton.addActionListener(e -> {
            trip.startRide();
            tripMap.put(trip.id, trip);
            saveTrips();
            JOptionPane.showMessageDialog(this, "Trip started! Total fare: $" + trip.calculateTotalFare());
            cardLayout.show(cardPanel, "MainPage");
        });

        // Start a timer to update the host screen periodically
        Timer timer = new Timer(5000, e -> updateHostTripInfo(participantsArea, trip));
        timer.start();
        participantsArea.putClientProperty("timer", timer);

        cardLayout.show(cardPanel, "HostTrip");
    }

    private JTextArea findTextArea(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JScrollPane) {
                JViewport viewport = ((JScrollPane) comp).getViewport();
                if (viewport.getView() instanceof JTextArea) {
                    return (JTextArea) viewport.getView();
                }
            }
        }
        throw new RuntimeException("JTextArea not found in panel");
    }

    // Helper method to find a JButton with a specific text in a panel
    private JButton findButton(JPanel panel, String buttonText) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                for (Component innerComp : ((JPanel) comp).getComponents()) {
                    if (innerComp instanceof JButton && ((JButton) innerComp).getText().equals(buttonText)) {
                        return (JButton) innerComp;
                    }
                }
            }
        }
        throw new RuntimeException("Button '" + buttonText + "' not found in panel");
    }


    private void updateHostTripInfo(JTextArea participantsArea, Trip trip) {
        StringBuilder info = new StringBuilder();
        info.append("Trip Number: ").append(trip.id).append("\n\n");
        info.append("Participants:\n");
        for (Map.Entry<User, Integer> entry : trip.passengers.entrySet()) {
            User passenger = entry.getKey();
            int seats = entry.getValue();
            info.append(passenger.name).append(" (").append(passenger.phone).append(") - ")
                .append(seats).append(" seat(s)\n");
        }
        info.append("\nAvailable Seats: ").append(trip.getAvailableSeats());

        participantsArea.setText(info.toString());
    }





    private void loadUsers() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(USER_FILE));
            users.clear(); // Clear existing users before loading
            System.out.println("Loading users. Total lines: " + lines.size()); // Debug print
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length == 5) {
                    User user = new User(parts[0], parts[1], parts[2], parts[3], parts[4]);
                    users.put(parts[1], user);
                    System.out.println("Loaded user: " + user.username); // Debug print
                }
            }
            System.out.println("Finished loading users. Total users: " + users.size()); // Debug print
        } catch (IOException e) {
            System.out.println("User file not found. Starting with empty user list.");
        }
    }

    private void loadTrips() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(TRIP_FILE));
            tripMap.clear();
            System.out.println("Loading trips. Total lines: " + lines.size());
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 9) { // Changed from 8 to 9 to include isStarted
                    User host = users.get(parts[1]);
                    if (host != null) {
                        Trip trip = new Trip(
                            host,
                            parts[2],
                            parts[3],
                            new Date(Long.parseLong(parts[4])),
                            new Date(Long.parseLong(parts[5])),
                            Integer.parseInt(parts[6]),
                            Double.parseDouble(parts[7])
                        );
                        trip.id = parts[0];
                        trip.isStarted = Boolean.parseBoolean(parts[8]); // Add this line
                        for (int i = 9; i < parts.length; i += 2) {
                            User passenger = users.get(parts[i]);
                            if (passenger != null) {
                                trip.bookSeats(Integer.parseInt(parts[i + 1]), passenger);
                            }
                        }
                        tripMap.put(trip.id, trip);
                        System.out.println("Loaded trip: " + trip);
                    }
                }
            }
            System.out.println("Finished loading trips. Total trips: " + tripMap.size());
        } catch (IOException e) {
            System.out.println("Trip file not found. Starting with empty trip list.");
        }
    }

    private void saveTrips() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(TRIP_FILE))) {
            for (Trip trip : tripMap.values()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join(",",
                    trip.id,
                    trip.host.username,
                    trip.origin,
                    trip.destination,
                    String.valueOf(trip.departureTime.getTime()),
                    String.valueOf(trip.arrivalTime.getTime()),
                    String.valueOf(trip.maxPassengers),
                    String.valueOf(trip.pricePerPassenger),
                    String.valueOf(trip.isStarted) // Add this line
                ));
                for (Map.Entry<User, Integer> entry : trip.passengers.entrySet()) {
                    sb.append(",").append(entry.getKey().username).append(",").append(entry.getValue());
                }
                writer.println(sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTripList(String origin, String destination) {
        SwingUtilities.invokeLater(() -> {
            Set<Trip> newDisplayedTrips = new HashSet<>();
            System.out.println("Updating trip list. Total trips: " + tripMap.size());
            for (Trip trip : tripMap.values()) {
                System.out.println("Checking trip: " + trip.id + 
                                   ", Origin: " + trip.origin + 
                                   ", Destination: " + trip.destination + 
                                   ", Available Seats: " + trip.getAvailableSeats() + 
                                   ", Is Full: " + trip.isFull() + 
                                   ", Is Started: " + trip.isStarted);
                if (trip.origin.equals(origin) && trip.destination.equals(destination) && !trip.isFull() && !trip.isStarted) {
                    newDisplayedTrips.add(trip);
                    System.out.println("Added trip to display: " + trip.id);
                }
            }
    
            listModel.clear();
            for (Trip trip : newDisplayedTrips) {
                listModel.addElement(trip);
            }
            System.out.println("Updated list model. New size: " + listModel.size());
        });
    }

    private void updateTripInformation() {
        while (true) {
            SwingUtilities.invokeLater(() -> {
                loadTrips(); // Reload trips from file
                if (tripList != null) {
                    String origin = "";
                    String destination = "";
                    
                    // Find the parent container of the tripList
                    Container parent = tripList.getParent();
                    while (parent != null && !(parent instanceof JPanel)) {
                        parent = parent.getParent();
                    }
                    
                    if (parent instanceof JPanel) {
                        JPanel searchPanel = (JPanel) ((JPanel) parent).getComponent(0);
                        if (searchPanel.getComponentCount() >= 4) {
                            JComboBox<?> originCombo = (JComboBox<?>) searchPanel.getComponent(1);
                            JComboBox<?> destinationCombo = (JComboBox<?>) searchPanel.getComponent(3);
                            origin = originCombo.getSelectedItem().toString();
                            destination = destinationCombo.getSelectedItem().toString();
                        }
                    }
                    
                    System.out.println("Updating trip information. Origin: " + origin + ", Destination: " + destination);
                    updateTripList(origin, destination);
                } else {
                    System.out.println("Trip list is null");
                }
            });
            try {
                Thread.sleep(5000); // Update every 5 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

///////////
// private void loadTrips() {
//     try {
//         List<String> lines = Files.readAllLines(Paths.get(TRIP_FILE));
//         tripMap.clear(); // Clear existing trips before loading
//         System.out.println("Loading trips. Total lines: " + lines.size()); // Debug print
//         for (String line : lines) {
//             String[] parts = line.split(",");
//             if (parts.length >= 8) {
//                 User host = users.get(parts[1]);
//                 if (host != null) {
//                     Trip trip = new Trip(
//                         host,
//                         parts[2],
//                         parts[3],
//                         new Date(Long.parseLong(parts[4])),
//                         new Date(Long.parseLong(parts[5])),
//                         Integer.parseInt(parts[6]),
//                         Double.parseDouble(parts[7])
//                     );
//                     trip.id = parts[0];
//                     for (int i = 8; i < parts.length; i += 2) {
//                         User passenger = users.get(parts[i]);
//                         if (passenger != null) {
//                             trip.bookSeats(Integer.parseInt(parts[i + 1]), passenger);
//                         }
//                     }
//                     tripMap.put(trip.id, trip);
//                     System.out.println("Loaded trip: " + trip); // Debug print
//                 }
//             }
//         }
//         System.out.println("Finished loading trips. Total trips: " + tripMap.size()); // Debug print
//     } catch (IOException e) {
//         System.out.println("Trip file not found. Starting with empty trip list.");
//     }
// }

    private void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_FILE))) {
            for (User user : users.values()) {
                writer.println(String.join(",", user.name, user.username, user.password, user.role, user.phone));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    private void createLoginPage() {
        JPanel loginPanel = new JPanel(new GridLayout(3, 2));
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("Login");
        JButton goToSignUpButton = new JButton("Sign Up");

        loginPanel.add(new JLabel("Username:"));
        loginPanel.add(usernameField);
        loginPanel.add(new JLabel("Password:"));
        loginPanel.add(passwordField);
        loginPanel.add(loginButton);
        loginPanel.add(goToSignUpButton);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (users.containsKey(username) && users.get(username).password.equals(password)) {
                currentUser = users.get(username);
                cardLayout.show(cardPanel, "MainPage");
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials");
            }
        });

        goToSignUpButton.addActionListener(e -> cardLayout.show(cardPanel, "SignUp"));

        cardPanel.add(loginPanel, "Login");
    }
    
    
    private void createSignUpPage() {
        JPanel signUpPanel = new JPanel(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
    
        JTextField nameField = new JTextField(20);
        JTextField usernameField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"Student", "Cab Driver"});
        JTextField phoneField = new JTextField(20);
        JTextField otpField = new JTextField(20);
        JButton signUpButton = new JButton("Sign Up");
    
        // Add components to the form panel
        addFormField(formPanel, gbc, "Name:", nameField);
        addFormField(formPanel, gbc, "Username:", usernameField);
        addFormField(formPanel, gbc, "Password:", passwordField);
        addFormField(formPanel, gbc, "Role:", roleCombo);
        addFormField(formPanel, gbc, "Phone:", phoneField);
        addFormField(formPanel, gbc, "OTP:", otpField);
    
        // Create a panel for the sign-up button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(signUpButton);
    
        // Add panels to the main sign-up panel
        signUpPanel.add(formPanel, BorderLayout.CENTER);
        signUpPanel.add(buttonPanel, BorderLayout.SOUTH);
    
        signUpButton.addActionListener(e -> {
            if (otpField.getText().equals("0000")) {
                String username = usernameField.getText();
                if (users.containsKey(username)) {
                    JOptionPane.showMessageDialog(this, "Username already exists");
                } else {
                    currentUser = new User(nameField.getText(), username, new String(passwordField.getPassword()),
                            roleCombo.getSelectedItem().toString(), phoneField.getText());
                    users.put(username, currentUser);
                    saveUsers();
                    cardLayout.show(cardPanel, "MainPage");
                }
            } else {
                JOptionPane.showMessageDialog(this, "Invalid OTP");
            }
        });
    
        cardPanel.add(signUpPanel, "SignUp");
    }
    
    // Helper method to add form fields
    private void addFormField(JPanel panel, GridBagConstraints gbc, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(label), gbc);
    
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }
    private void createMainPage() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Post Trip", createPostTripPanel());
        tabbedPane.addTab("Search Trips", createSearchTripsPanel());
        cardPanel.add(tabbedPane, "MainPage");
    }

    private JPanel createPostTripPanel() {
        JPanel postTripPanel = new JPanel(new GridLayout(9, 2));
        JComboBox<String> originCombo = new JComboBox<>(new String[]{"A", "B", "C"});
        JComboBox<String> destinationCombo = new JComboBox<>(new String[]{"A", "B", "C"});
        JSpinner departureTimeSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner arrivalTimeSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner maxPassengersSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        JTextField priceField = new JTextField();
        JButton postTripButton = new JButton("Post Trip");
        JButton beginRideButton = new JButton("Begin Ride");


        postTripPanel.add(new JLabel("Origin:"));
        postTripPanel.add(originCombo);
        postTripPanel.add(new JLabel("Destination:"));
        postTripPanel.add(destinationCombo);
        postTripPanel.add(new JLabel("Departure Time:"));
        postTripPanel.add(departureTimeSpinner);
        postTripPanel.add(new JLabel("Arrival Time:"));
        postTripPanel.add(arrivalTimeSpinner);
        postTripPanel.add(new JLabel("Max Passengers:"));
        postTripPanel.add(maxPassengersSpinner);
        postTripPanel.add(new JLabel("Price per Passenger:"));
        postTripPanel.add(priceField);
        postTripPanel.add(postTripButton);
        postTripPanel.add(beginRideButton);

        postTripButton.addActionListener(e -> {
            Trip newTrip = new Trip(
                currentUser,
                originCombo.getSelectedItem().toString(),
                destinationCombo.getSelectedItem().toString(),
                (Date) departureTimeSpinner.getValue(),
                (Date) arrivalTimeSpinner.getValue(),
                (int) maxPassengersSpinner.getValue(),
                Double.parseDouble(priceField.getText())
            );
            tripMap.put(newTrip.id, newTrip);
            saveTrips();
            JOptionPane.showMessageDialog(this, "Trip posted successfully!");
            showHostTripScreen(newTrip);
        });
    


        beginRideButton.addActionListener(e -> {
            Optional<Trip> fullTrip = tripMap.values().stream()
                .filter(trip -> trip.host.equals(currentUser) && trip.isFull() && !trip.isStarted)
                .findFirst();
            
            if (fullTrip.isPresent()) {
                Trip trip = fullTrip.get();
                trip.startRide();
                tripMap.put(trip.id, trip); // Update the trip in the map
                saveTrips(); // Save the updated trips to file
                JOptionPane.showMessageDialog(this, "Ride started! Total fare: $" + trip.calculateTotalFare());
                updateTripList(trip.origin, trip.destination); // Update the trip list
            } else {
                JOptionPane.showMessageDialog(this, "No full trips available to start");
            }
        });
        return postTripPanel;
    }

    private JPanel createSearchTripsPanel() {
        JPanel searchTripsPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new GridLayout(3, 2));
        JComboBox<String> originCombo = new JComboBox<>(new String[]{"A", "B", "C"});
        JComboBox<String> destinationCombo = new JComboBox<>(new String[]{"A", "B", "C"});
        JButton searchButton = new JButton("Search");

        searchPanel.add(new JLabel("Origin:"));
        searchPanel.add(originCombo);
        searchPanel.add(new JLabel("Destination:"));
        searchPanel.add(destinationCombo);
        searchPanel.add(new JLabel());
        searchPanel.add(searchButton);

        tripList = new JList<>(listModel);

        searchButton.addActionListener(e -> {
            updateTripList(originCombo.getSelectedItem().toString(), destinationCombo.getSelectedItem().toString());
        });

        tripList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    Trip selectedTrip = tripList.getSelectedValue();
                    if (selectedTrip != null) {
                        showBookingDialog(selectedTrip);
                    }
                }
            }
        });

        searchTripsPanel.add(searchPanel, BorderLayout.NORTH);
        searchTripsPanel.add(new JScrollPane(tripList), BorderLayout.CENTER);

        return searchTripsPanel;
    }



    private void showBookingDialog(Trip trip) {
        JDialog bookingDialog = new JDialog(this, "Book Trip", true);
        bookingDialog.setLayout(new GridLayout(4, 2));
    
        JLabel tripInfoLabel = new JLabel(trip.toString());
        JTextField pickupPointField = new JTextField();
        JSpinner seatsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, trip.getAvailableSeats(), 1));
        JLabel priceLabel = new JLabel("Price: $" + trip.pricePerPassenger);
        JButton bookButton = new JButton("Book");
    
        bookingDialog.add(tripInfoLabel);
        bookingDialog.add(new JLabel());
        bookingDialog.add(new JLabel("Pickup Point:"));
        bookingDialog.add(pickupPointField);
        bookingDialog.add(new JLabel("Number of Seats:"));
        bookingDialog.add(seatsSpinner);
        bookingDialog.add(priceLabel);
        bookingDialog.add(bookButton);
    
        seatsSpinner.addChangeListener(e -> {
            int seats = (int) seatsSpinner.getValue();
            priceLabel.setText("Price: $" + (seats * trip.pricePerPassenger));
        });
    
        bookButton.addActionListener(e -> {
            int seats = (int) seatsSpinner.getValue();
            String pickupPoint = pickupPointField.getText();
            if (pickupPoint.isEmpty()) {
                JOptionPane.showMessageDialog(bookingDialog, "Please enter a pickup point");
            } else {
                if (trip.bookSeats(seats, currentUser)) {
                    tripMap.put(trip.id, trip);
                    saveTrips();
                    JOptionPane.showMessageDialog(bookingDialog, "Booking successful!\nTotal Price: $" + (seats * trip.pricePerPassenger));
                    bookingDialog.dispose();
                    showPassengerWaitingScreen(trip);
                } else {
                    JOptionPane.showMessageDialog(bookingDialog, "Not enough seats available");
                }
            }
        });

    
        bookingDialog.pack();
        bookingDialog.setVisible(true);
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(CabSharingSystem::new);

    }
}

class User {
    String name;
    String username;
    String password;
    String role;
    String phone;

    public User(String name, String username, String password, String role, String phone) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.role = role;
        this.phone = phone;
    }

    @Override
    public String toString() {
        return name + " (" + phone + ")";
    }
}

class Trip {
    String id; // Add this line
    User host;
    String origin;
    String destination;
    Date departureTime;
    Date arrivalTime;
    int maxPassengers;
    double pricePerPassenger;
    Map<User, Integer> passengers = new ConcurrentHashMap<>();
    boolean isStarted = false;

    public Trip(User host, String origin, String destination, Date departureTime, Date arrivalTime, int maxPassengers, double pricePerPassenger) {
        this.id = UUID.randomUUID().toString(); // Add this line
        this.host = host;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.maxPassengers = maxPassengers;
        this.pricePerPassenger = pricePerPassenger;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trip trip = (Trip) o;
        return id.equals(trip.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }



    public synchronized boolean bookSeats(int seats, User user) {
        if (getAvailableSeats() >= seats) {
            passengers.put(user, passengers.getOrDefault(user, 0) + seats);
            return true;
        }
        return false;
    }

    public synchronized int getAvailableSeats() {
        int bookedSeats = passengers.values().stream().mapToInt(Integer::intValue).sum();
        return maxPassengers - bookedSeats;
    }


    public synchronized boolean isFull() {
        boolean full = getAvailableSeats() == 0;
        if (full) {
            System.out.println("Trip " + id + " is now full!");
        }
        return full;
    }


    public void startRide() {
        this.isStarted = true;
    }



    public double calculateTotalFare() {
        return passengers.values().stream().mapToInt(Integer::intValue).sum() * pricePerPassenger;
    }


    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return String.format("From %s to %s, Departure: %s, Arrival: %s, Host: %s, Available Seats: %d, Price: $%.2f",
                origin, destination, sdf.format(departureTime), sdf.format(arrivalTime), host, getAvailableSeats(), pricePerPassenger);
    }
}