import org.sqlite.SQLiteDataSource;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TCPServer {
    public static Integer selected_causeID;
    public static Integer selectedProblemID;

    public static void main(String argv[]) throws Exception {
        ServerSocket welcomeSocket = new ServerSocket(6789);

        while (true) {
            try {
                Socket connectionSocket = welcomeSocket.accept();

                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

                // Listing possible car problems
                listCarProblems(outToClient, inFromClient);
                // Listing possible causes for this problem
                listPossibleCauses(outToClient, inFromClient);
                // Displaying solution for selected problem
                displaySolution(outToClient);

                connectionSocket.close();
            } catch (SocketException e) {
                // Handle socket exception (e.g., client closed connection)
                System.out.println("Client closed the connection.");
            }
        }
    }

    // Listing possible car problems
    static void listCarProblems(DataOutputStream outToClient, BufferedReader inFromClient) throws IOException, SQLException {
        SQLiteDataSource carProblems = new SQLiteDataSource();
        carProblems.setUrl("jdbc:sqlite:CarProblems.db");

        Connection connection = carProblems.getConnection();
        Statement statement = connection.createStatement();

        String query = "SELECT id, problems FROM car_problems";
        ResultSet resultSet = statement.executeQuery(query);

        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String problems = resultSet.getString("problems");
            String problemslist = "ID " + id + ": Problems: " + problems;
            outToClient.writeBytes(problemslist + '\n');
        }

        // Key for making client stop listening and send answer
        outToClient.writeBytes("answer\n");
        outToClient.flush();

        int problemid = Integer.parseInt(inFromClient.readLine());
        System.out.println("Received problem id from the client: " + problemid);
        selectedProblemID = problemid;

        resultSet.close();
        statement.close();
        connection.close();
    }

    // Listing possible causes for selected problem
    static void listPossibleCauses(DataOutputStream outToClient, BufferedReader inFromClient) throws IOException, SQLException {
        selected_causeID = 1;

        SQLiteDataSource carProblems = new SQLiteDataSource();
        carProblems.setUrl("jdbc:sqlite:CarProblems.db");

        Connection connection = carProblems.getConnection();
        Statement statement = connection.createStatement();

        String query = "SELECT id2, subquestions FROM Possible_Causes WHERE id1 =" + selectedProblemID;
        ResultSet resultSet = statement.executeQuery(query);

        // boolean for checking is problem founded from listed possible causes.
        boolean isFound = false;
        // Bringing possible causes to user in order as questions.
        while (resultSet.next()) {
            String subquestions = resultSet.getString("subquestions");
            int id2 = resultSet.getInt("id2");

            outToClient.writeBytes("ID:" + id2 + ", " + subquestions + "\n");
            outToClient.flush();
            // Given answer to displayed question (YES/NO).
            String causeAnswer = inFromClient.readLine().toString();
            System.out.println("Answer to the given question: " + causeAnswer);

            if ("YES".equals(causeAnswer)) {
                selected_causeID = id2;
                isFound = true;
                break;
            }
            // Moving to next question if it is not suitable cause for client.
            selected_causeID++;
        }
        // Sending connection closer message "END" to client if cause couldn't find.
        if (!isFound) {
            outToClient.writeBytes("END\n");
            System.out.println("Client couldn't find the cause of the problem...");
        }

        resultSet.close();
        statement.close();
        connection.close();
    }

    // Displaying solution for selected problem
    static void displaySolution(DataOutputStream outToClient) throws IOException, SQLException {
        SQLiteDataSource carProblems = new SQLiteDataSource();
        carProblems.setUrl("jdbc:sqlite:CarProblems.db");

        Connection connection = carProblems.getConnection();
        Statement statement = connection.createStatement();

        String query = "SELECT solutions FROM Possible_Causes WHERE id1 =" + selectedProblemID + " AND id2=" + selected_causeID;
        ResultSet resultSet = statement.executeQuery(query);

        // Sending suitable solution to client for given problem and its cause.
        if (resultSet.next()) {
            String solution = resultSet.getString("solutions");

            outToClient.writeBytes("***  " + solution + "  ***\n");
            outToClient.writeBytes("***  " + solution + "  ***\n");
            outToClient.flush();
            System.out.println("Solution found for the client...");
        } else {
            System.out.println("No solution found for the given problem and cause.");
        }

        resultSet.close();
        statement.close();
        connection.close();
    }
}
