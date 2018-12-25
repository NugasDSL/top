package top.app;
import top.server.Top;
import toy.config.Config;
import toy.servers.Server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JTop {
    private static org.apache.log4j.Logger logger;
    static Server s;
    static String type;
    public static void main(String argv[]) {
        mainImpl(argv);
//
    }

    static String[] getArgs(String cmd) {
        List<String> matchList = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");
        Matcher regexMatcher = regex.matcher(cmd);
        while (regexMatcher.find()) {
            matchList.add(regexMatcher.group());
        }
        return matchList.toArray(new String[0]);
    }

    static void mainImpl(String argv[]) {
        try {
            Path config = null;
            if (argv.length == 3) {
                config = Paths.get(argv[2]);
            }

            int serverID = Integer.parseInt(argv[0]);
            int listenerPort = Integer.parseInt(argv[1]);
            Config.setConfig(config, serverID);
            logger = org.apache.log4j.Logger.getLogger(cli.class);

            s = new Top(Config.getAddress(serverID), listenerPort, Config.getPort(serverID), serverID, Config.getF(), Config.getC()
                    , Config.getTMO(), Config.getTMOInterval(), Config.getMaxTransactionsInBlock(), Config.getFastMode()
                    , Config.getCluster(), Config.getRMFbbcConfigHome(), Config.getPanicRBConfigHome()
                    , Config.getSyncRBConfigHome()
                    , Config.getServerCrtPath(), Config.getServerTlsPrivKeyPath(), Config.getCaRootPath());

            cli parser = new cli();
            Scanner scan = new Scanner(System.in).useDelimiter("\\n");
            while (true) {
                System.out.print("Toy>> ");
                if (!scan.hasNext()) {
                    break;
                }
                parser.parse(getArgs(scan.next()));
            }
        } catch (Exception ex) {
            logger.error("", ex);
            System.exit(0);
        }
    }
}
