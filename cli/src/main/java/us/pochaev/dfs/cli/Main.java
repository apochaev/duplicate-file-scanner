package us.pochaev.dfs.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Scans the folders given and writes the duplicates found to stdout as JSON Lines (DFS-020). */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(Main.class, args)));
    }
}
