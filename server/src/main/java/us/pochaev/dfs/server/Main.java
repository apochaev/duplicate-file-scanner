package us.pochaev.dfs.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The local web server for exploring a scan (docs/design/ui.md); for now a skeleton on loopback (DFS-035). */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
