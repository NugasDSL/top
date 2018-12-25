package top.app;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import java.util.*;
import static java.lang.String.format;

public class cli {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(cli.class);
        private Options options = new Options();
        public cli() {
            options.addOption("help", "print this message");
            options.addOption("init", "init the server");
            options.addOption("serve", "start the server\n" +
                    "Note that before lunching the command, all other servers has to be initialized first");
            options.addOption("stop", "terminate the server");
            options.addOption("quit", "exit Toy terminal");
        }

        void parse(String[] args) {
                if (args[0].equals("help")) {
                    help();
                    return;
                }
                if (args[0].equals("init")) {
                    init();
                    System.out.println("Init server... [OK]");
                    return;
                }

                if (args[0].equals("serve")) {
                    serve();
                    System.out.println("Serving... [OK]");
                    return;
                }
                if (args[0].equals("stop")) {
                    stop();
                    System.out.println("stop server... [OK]");
                    return;
                }
                if (args[0].equals("quit")) {
                    System.exit(0);
                    return;
                }

               logger.error(format("Invalid command %s", Arrays.toString(args)));

        }
        private void help() {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Optimistic Total Ordering System ", options);
        }

        private void init() {
            JTop.s.start();
        }

        private void serve() {
            logger.debug("start serving");
            JTop.s.serve();
        }
        private void stop() {
            JTop.s.shutdown();
        }

}
