package com.loyalty;

import com.loyalty.cli.Cli;
import com.loyalty.service.LoyaltyService;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Map;

/**
 * Entry point. {@code serve} starts the REST API; every other command starts Spring without a web
 * server, runs one CLI command, and exits.
 */
@SpringBootApplication
public class LoyaltyApplication {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            System.out.print(Cli.USAGE_TEXT);
            System.exit(args.length == 0 ? Cli.USAGE : Cli.OK);
        }

        String command = args[0];
        String[] options = Arrays.copyOfRange(args, 1, args.length);

        if (command.equals("serve")) {
            serve(options);
        } else {
            System.exit(runCliCommand(command, options));
        }
    }

    private static void serve(String[] options) {
        Map<String, String> parsed;
        try {
            parsed = Cli.parseOptions(options);
        } catch (RuntimeException e) {
            System.err.println("Invalid serve options: " + e.getMessage());
            System.exit(Cli.USAGE);
            return;
        }
        String[] springArgs = parsed.containsKey("port")
                ? new String[] {"--server.port=" + parsed.get("port")}
                : new String[0];
        SpringApplication.run(LoyaltyApplication.class, springArgs);
    }

    private static int runCliCommand(String command, String[] options) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(LoyaltyApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("logging.level.root=WARN")
                .run();
        int exitCode = new Cli(context.getBean(LoyaltyService.class), System.out, System.err).run(command, options);
        return SpringApplication.exit(context, () -> exitCode);
    }
}
