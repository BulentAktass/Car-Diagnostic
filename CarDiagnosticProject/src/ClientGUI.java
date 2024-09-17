import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class ClientGUI {
    private Socket clientSocket;
    private DataOutputStream outToServer;
    private BufferedReader inFromServer;
    private JFrame frame;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private String solution = "-";
    private JLabel finalsolution = new JLabel();

    // Constructor of GUI with Socket and Server implementations.
    public ClientGUI() throws IOException {
        clientSocket = new Socket("localhost", 6789);
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SwingUtilities.invokeLater(() -> {
            try {
                ClientGUI gui = new ClientGUI();
                gui.GUIPanelsystem();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // GUI structure with Panel system.
    public void GUIPanelsystem() throws IOException, InterruptedException {
        frame = new JFrame("Car Diagnostic");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);

        // Create the first panel
        JPanel firstPanel = createFirstPanel();
        cardPanel.add(firstPanel, "firstPanel");

        // Create the second panel
        JPanel secondPanel = createSecondPanel();
        cardPanel.add(secondPanel, "secondPanel");

        // Create the final YES panel
        JPanel finalYESPanel = createFinalYESPanel();
        cardPanel.add(finalYESPanel, "finalYESPanel");

        // Create the final NO panel
        JPanel finalNOPanel = createFinalNOPanel();
        cardPanel.add(finalNOPanel, "finalNOPanel");

        // Show the first panel initially
        cardLayout.show(cardPanel, "firstPanel");

        frame.getContentPane().add(cardPanel);
        frame.setVisible(true);
    }

    // First Page
    private JPanel createFirstPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("<html><h2>Car Diagnostic</h2><p>Select a Car Problem</p></html>");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        List<String> problems = new ArrayList<>();
        // Listening server and adding problems to List till received "answer".
        while (!clientSocket.isClosed()) {
            String sentence;
            try {
                sentence = inFromServer.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (sentence.equals("answer")) {
                break;
            }
            if (sentence.startsWith("ID")) {
                problems.add(sentence);
            }
        }
        // Creating ComboBox with problems List.
        JComboBox<String> comboBox = new JComboBox<>(new DefaultComboBoxModel<>(problems.toArray(new String[0])));
        comboBox.setMaximumSize(new Dimension(600, 30));

        JButton sendButton = new JButton("Choose!");
        sendButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        sendButton.addActionListener(e -> {
            int selectedIndex = comboBox.getSelectedIndex();
            int problemId = selectedIndex + 1;
            try {
                outToServer.writeBytes(problemId + "\n");
                outToServer.flush();
                // Switch to the second panel after clicking "Choose!"
                cardLayout.show(cardPanel, "secondPanel");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(comboBox);
        panel.add(Box.createVerticalStrut(10));
        panel.add(sendButton);

        return panel;
    }

    // Second Page
    private JPanel createSecondPanel() throws IOException {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("<html><h2>Possible Causes</h2><p>If you found the given cause in your car, press 'YES'</p></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JButton yesButton = new JButton("YES");
        JButton noButton = new JButton("NO");
        JLabel questionField = new JLabel("");
        questionField.setHorizontalAlignment(SwingConstants.CENTER);

        new Thread(() -> {
            try {
                while (true) {
                    String question = inFromServer.readLine();
                    // If "END" message received from server; that means possible cause couldn't find.
                    if (question.equals("END")) {
                        clientSocket.close();
                        cardLayout.show(cardPanel, "finalNOPanel");
                    }
                    // Showing current question from server.
                    questionField.setText(question);
                    questionField.repaint();
                }
            } catch (SocketException se) {
                System.out.println("Socket closed.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Sending YES answer to server.
        yesButton.addActionListener(e -> {
            try {
                outToServer.writeBytes("YES\n");
                outToServer.flush();
                // After clicking YES; fetch the solution for selected cause.
                new Thread(() -> {
                    try {
                        String solution2 = inFromServer.readLine();
                        setSolution(solution2);
                        // Refresh the solution page before moving.
                        updateFinalPanel();
                        clientSocket.close();

                    } catch (SocketException se) {
                        System.out.println("Socket closed.");
                    } catch (IOException f) {
                        f.printStackTrace();
                    }
                }).start();

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            cardLayout.show(cardPanel, "finalYESPanel");
        });

        // Sending NO answer to server.
        noButton.addActionListener(e -> {
            try {
                outToServer.writeBytes("NO\n");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(questionField, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    public JPanel createFinalYESPanel() throws IOException, InterruptedException {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("<html><h2>Solution</h2></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        finalsolution.setHorizontalAlignment(SwingConstants.CENTER);
        JButton restartButton = new JButton("RESTART");
        restartButton.addActionListener(e -> restartConnection());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(restartButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(finalsolution, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFinalNOPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("<html><h2>Sorry!</h2><p>We couldn't find the possible cause for this problem. Call for professional service...</p></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JButton restartButton = new JButton("RESTART");
        restartButton.addActionListener(e -> restartConnection());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(restartButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public void updateFinalPanel() {
        finalsolution.setText(solution);
        finalsolution.repaint();
    }

    private void restartConnection() {
        try {
            // Close the existing socket and streams
            clientSocket.close();
            outToServer.close();
            inFromServer.close();

            // Create a new socket and streams
            clientSocket = new Socket("localhost", 6789);
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            // Recreate the GUI panel system
            cardPanel.removeAll();
            // Create the first panel
            JPanel firstPanel = createFirstPanel();
            cardPanel.add(firstPanel, "firstPanel");
            // Create the second panel
            JPanel secondPanel = createSecondPanel();
            cardPanel.add(secondPanel, "secondPanel");
            // Create the final YES panel
            JPanel finalYESPanel = createFinalYESPanel();
            cardPanel.add(finalYESPanel, "finalYESPanel");
            // Create the final NO panel
            JPanel finalNOPanel = createFinalNOPanel();
            cardPanel.add(finalNOPanel, "finalNOPanel");
            // Show the first panel initially
            cardLayout.show(cardPanel, "firstPanel");

            frame.getContentPane().add(cardPanel);
            frame.validate();
            frame.repaint();

        } catch (SocketException se) {
            System.out.println("Socket closed.");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
