package com.loyalty.cli;

import com.loyalty.domain.BalanceSummary;
import com.loyalty.domain.EarnResult;
import com.loyalty.domain.RedemptionResult;
import com.loyalty.domain.RefundResult;
import com.loyalty.domain.Reward;
import com.loyalty.error.InvalidRequestException;
import com.loyalty.error.LoyaltyException;
import com.loyalty.service.LoyaltyService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Command-line adapter over {@link LoyaltyService}. Talks to the database directly, so it works
 * with or without the HTTP server running.
 *
 * <p>Exit codes: 0 success, 1 business rule rejected the request, 2 usage error.
 */
public final class Cli {

    public static final int OK = 0;
    public static final int REJECTED = 1;
    public static final int USAGE = 2;

    public static final String USAGE_TEXT = """
            Usage: points <command> [options]

              balance  --user=<id>
              earn     --user=<id> --purchase-id=<id> --amount=<dollars>
              redeem   --user=<id> --reward=<reward-id>
              refund   --user=<id> --purchase-id=<id>
              rewards
              serve    [--port=7070]        start the REST API
            """;

    private final LoyaltyService service;
    private final PrintStream out;
    private final PrintStream err;

    public Cli(LoyaltyService service, PrintStream out, PrintStream err) {
        this.service = service;
        this.out = out;
        this.err = err;
    }

    public int run(String command, String[] optionArgs) {
        try {
            Map<String, String> options = parseOptions(optionArgs);
            switch (command) {
                case "balance" -> printBalance(service.balance(required(options, "user")));
                case "earn" -> printEarn(service.recordPurchase(
                        required(options, "user"), required(options, "purchase-id"), amount(options)));
                case "redeem" -> printRedemption(service.redeem(required(options, "user"), required(options, "reward")));
                case "refund" -> printRefund(service.refundPurchase(
                        required(options, "user"), required(options, "purchase-id")));
                case "rewards" -> service.listRewards().forEach(this::printReward);
                default -> {
                    err.println("Unknown command: " + command);
                    err.print(USAGE_TEXT);
                    return USAGE;
                }
            }
            return OK;
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.print(USAGE_TEXT);
            return USAGE;
        } catch (LoyaltyException e) {
            err.println("Error (" + e.code() + "): " + e.getMessage());
            return REJECTED;
        }
    }

    private void printBalance(BalanceSummary b) {
        out.printf("%s: %d points available | tier %s | 12-month spend $%s%n",
                b.customerId(), b.availablePoints(), b.tier(), b.rollingSpend().toPlainString());
        if (b.pointsOwed() > 0) {
            out.printf("  %d points owed from refunds will be deducted from future earnings%n", b.pointsOwed());
        }
    }

    private void printEarn(EarnResult r) {
        out.printf("Recorded purchase %s: earned %d points", r.purchaseId(), r.pointsEarned());
        if (r.expiresAt() != null) {
            out.printf(" (expire %s)", r.expiresAt());
        }
        out.println();
        if (r.pointsToOwed() > 0) {
            out.printf("  %d of those points repaid points owed from earlier refunds%n", r.pointsToOwed());
        }
        printBalance(r.balance());
    }

    private void printRedemption(RedemptionResult r) {
        out.printf("Redeemed '%s' for %d points (redemption #%d)%n",
                r.reward().name(), r.reward().costPoints(), r.redemptionId());
        printBalance(r.balance());
    }

    private void printRefund(RefundResult r) {
        out.printf("Refunded purchase %s: %d points clawed back%n", r.purchaseId(), r.pointsClawedBack());
        if (r.pointsAddedToOwed() > 0) {
            out.printf("  %d already-spent points could not be recovered and are now owed%n", r.pointsAddedToOwed());
        }
        printBalance(r.balance());
    }

    private void printReward(Reward reward) {
        out.printf("%-18s %5d points  %s%n", reward.id(), reward.costPoints(), reward.name());
    }

    /** Accepts both {@code --key=value} and {@code --key value}. */
    public static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new UsageException("Unexpected argument: " + arg);
            }
            String key;
            String value;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                key = arg.substring(2, eq);
                value = arg.substring(eq + 1);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                key = arg.substring(2);
                value = args[++i];
            } else {
                throw new UsageException("Missing value for " + arg);
            }
            options.put(key, value);
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new UsageException("Missing required option --" + key);
        }
        return value;
    }

    private static BigDecimal amount(Map<String, String> options) {
        String raw = required(options, "amount").replace("$", "");
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Amount '" + raw + "' is not a number");
        }
    }

    static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
