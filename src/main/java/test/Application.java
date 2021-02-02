package test;

import jade.Boot;

public class Application {
    public static void main(String[] args) {
        String[] parameters = new String[] { //"-gui", // Starts the agent management tools
                "-host", "localhost", // Replace this with your local IP.
                "-port", "10207", // The port number for inter-platform communication
                "agentLauncher:test.AgentLauncher()"};

        Boot.main(parameters);

    }
}
