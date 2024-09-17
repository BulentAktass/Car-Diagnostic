import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestClass {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final ByteArrayInputStream inputStream = new ByteArrayInputStream("1\nYES\n".getBytes());
    private final PrintStream originalOut = System.out;
    private TCPServer server;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outputStream));
        System.setIn(inputStream);
        server = new TCPServer();
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setIn(System.in);
    }

    @Test
    public void testListCarProblems() throws IOException, SQLException {
        // Assuming that the database has some test data
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStream)));
        String output = outputStream.toString();
        assertEquals(true, output.contains("ID"));
    }

    @Test
    public void testListPossibleCausesWithYES() throws IOException, SQLException {
        // Initialize ByteArrayInputStream with the required inputs
        ByteArrayInputStream inputStreamForProblem = new ByteArrayInputStream("1\n".getBytes());
        ByteArrayInputStream inputStreamForCauses = new ByteArrayInputStream("YES\n".getBytes());

        // Execute the listCarProblems method: Selecting first problem.
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForProblem)));

        // Execute the listPossibleCauses method: Answering YES to first cause.
        server.listPossibleCauses(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForCauses)));
        String output = outputStream.toString();

        // Check if the output contains the expected messages in order
        assertEquals(true, output.contains("Received problem id from the client: 1"));
        assertEquals(true, output.contains("Answer to the given question: YES"));

    }
    @Test
    public void testListPossibleCausesWithNO() throws IOException, SQLException {
        // Initialize ByteArrayInputStream with the required input
        ByteArrayInputStream inputStreamForProblem = new ByteArrayInputStream("1\n".getBytes());
        ByteArrayInputStream inputStreamForCauses = new ByteArrayInputStream("NO\nYES\n".getBytes());

        // Execute the listCarProblems method: Selecting first problem.
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForProblem)));

        // Execute the listPossibleCauses method: Answering NO to first cause.
        server.listPossibleCauses(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForCauses)));
        String output = outputStream.toString();

        // Check if the output contains the expected messages in order
        assertEquals(true, output.contains("ID:1"));
        assertEquals(true, output.contains("Answer to the given question: NO"));

        // Check if it moved to the second question
        assertEquals(true, output.contains("ID:2"));
    }
    @Test
    public void testListPossibleCausesCauseCouldntFind() throws IOException, SQLException {
        // Initialize ByteArrayInputStream with the required input
        ByteArrayInputStream inputStreamForProblem = new ByteArrayInputStream("1\n".getBytes());

        // Execute the listCarProblems method: Selecting first problem.
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForProblem)));

        SQLiteDataSource carProblems = new SQLiteDataSource();
        carProblems.setUrl("jdbc:sqlite:CarProblems.db");

        Connection connection = carProblems.getConnection();
        Statement statement = connection.createStatement();

        String query = "SELECT id2, subquestions FROM Possible_Causes WHERE id1 =" + 1; // Selected problem is: 1
        ResultSet resultSet = statement.executeQuery(query);

        // Answering NO for each cause based on the size of the ResultSet.
        StringBuilder noAnswers = new StringBuilder();
        while (resultSet.next()) {
            noAnswers.append("NO\n");
        }

        ByteArrayInputStream inputStreamForCauses = new ByteArrayInputStream(noAnswers.toString().getBytes());
        server.listPossibleCauses(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForCauses)));

        // Check the output
        String output = outputStream.toString();

        assertEquals(true, output.contains("END"));
        assertEquals(true, output.contains("Client couldn't find the cause of the problem..."));
    }



    @Test
    public void testDisplaySolution() throws IOException, SQLException {

        // Initialize ByteArrayInputStream with the required inputs
        ByteArrayInputStream inputStreamForProblem = new ByteArrayInputStream("1\n".getBytes());
        ByteArrayInputStream inputStreamForCauses = new ByteArrayInputStream("YES\n".getBytes());

        // Execute the listCarProblems method: Selecting first problem.
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForProblem)));

        // Execute the listPossibleCauses method: Answering YES to first cause.
        server.listPossibleCauses(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForCauses)));

        server.displaySolution(new DataOutputStream(outputStream));
        String output = outputStream.toString();

        assertEquals(true, output.contains("***"));
        assertEquals(true, output.contains("Solution found for the client..."));
    }

    @Test
    public void testDisplaySolutionNoResult() throws IOException, SQLException {
        // Initialize ByteArrayInputStream with the required input
        ByteArrayInputStream inputStreamForProblem = new ByteArrayInputStream("1\n".getBytes());

        // Execute the listCarProblems method: Selecting first problem.
        server.listCarProblems(new DataOutputStream(outputStream), new BufferedReader(new InputStreamReader(inputStreamForProblem)));

        // Execute the displaySolution method: Set selected_causeID to a value where no solution is found.
        server.selected_causeID = 999; // 999 is a value where no solution is present (SQL error).

        server.displaySolution(new DataOutputStream(outputStream));
        String output = outputStream.toString();

        assertEquals(true, output.contains("No solution found for the given problem and cause."));
    }

}
