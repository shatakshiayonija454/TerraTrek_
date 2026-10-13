import java.util.*;
import java.util.stream.*;

/**
 * Trip Planner: compares transport options by emissions, cost and time.
 * Compile: javac TripPlanner.java
 * Run:     java TripPlanner
 */
public class TripPlanner {

    // ---------- Transport modes ----------
    // co2PerKm: kg CO2 per passenger-km (approximate averages)
    // costPerKm: currency units per km, baseCost: fixed fare/booking cost
    // speedKmh: average door-to-door moving speed, overheadHours: boarding/security/waiting time
    // maxKm: practical distance limit for the mode
    enum Mode {
        WALK  ("Walk",  0.000,  0.0,   0,   5,  0.0,   20),
        BIKE  ("Bike",  0.000,  0.0,   0,  15,  0.0,   80),
        BUS   ("Bus",   0.089,  1.5,  50,  45,  0.5, 1500),
        TRAIN ("Train", 0.041,  2.0,  80,  80,  0.5, 3000),
        CAR   ("Car",   0.171,  6.0,   0,  60,  0.1, 3000),
        FLIGHT("Flight",0.255,  5.0, 1500, 750, 3.0, 20000);

        final String label;
        final double co2PerKm, costPerKm, baseCost, speedKmh, overheadHours, maxKm;

        Mode(String label, double co2PerKm, double costPerKm, double baseCost,
             double speedKmh, double overheadHours, double maxKm) {
            this.label = label;
            this.co2PerKm = co2PerKm;
            this.costPerKm = costPerKm;
            this.baseCost = baseCost;
            this.speedKmh = speedKmh;
            this.overheadHours = overheadHours;
            this.maxKm = maxKm;
        }
    }

    // ---------- One computed option ----------
    static class TripOption {
        final Mode mode;
        final double distanceKm, emissionsKg, cost, hours; // emissionsKg and cost are TOTAL for the whole group
        final double costPerPerson, emissionsPerPerson;
        double score; // lower is better, set after normalisation

        final int units; // tickets for most modes, number of cars for CAR

        TripOption(Mode mode, double distanceKm, int travellers, int seatsPerCar) {
            this.mode = mode;
            this.distanceKm = distanceKm;
            if (mode == Mode.CAR) {
                // Car: cost and emissions are per vehicle; passengers decide how many cars are needed
                this.units = (int) Math.ceil((double) travellers / seatsPerCar);
                this.emissionsKg = distanceKm * mode.co2PerKm * units;
            } else {
                this.units = travellers;
                this.emissionsKg = distanceKm * mode.co2PerKm * units;
            }
            this.cost = (mode.baseCost + distanceKm * mode.costPerKm) * units;
            this.hours = mode.overheadHours + distanceKm / mode.speedKmh;
            // Per-person share: the car's total is split between everyone travelling
            this.costPerPerson = this.cost / travellers;
            this.emissionsPerPerson = this.emissionsKg / travellers;
        }
    }

    // ---------- Planner logic ----------
    static List<TripOption> plan(double distanceKm, int travellers, int seatsPerCar) {
        return Arrays.stream(Mode.values())
                .filter(m -> distanceKm <= m.maxKm)
                .map(m -> new TripOption(m, distanceKm, travellers, seatsPerCar))
                .collect(Collectors.toList());
    }

    /** Weighted score using min-max normalisation. Weights should sum to 1. */
    static void score(List<TripOption> options, double wCost, double wTime, double wEmis) {
        double minC = min(options, o -> o.costPerPerson),        maxC = max(options, o -> o.costPerPerson);
        double minT = min(options, o -> o.hours),       maxT = max(options, o -> o.hours);
        double minE = min(options, o -> o.emissionsPerPerson), maxE = max(options, o -> o.emissionsPerPerson);
        for (TripOption o : options) {
            o.score = wCost * norm(o.costPerPerson, minC, maxC)
                    + wTime * norm(o.hours, minT, maxT)
                    + wEmis * norm(o.emissionsPerPerson, minE, maxE);
        }
    }

    static double norm(double v, double min, double max) {
        return max == min ? 0 : (v - min) / (max - min);
    }
    static double min(List<TripOption> l, java.util.function.ToDoubleFunction<TripOption> f) {
        return l.stream().mapToDouble(f).min().orElse(0);
    }
    static double max(List<TripOption> l, java.util.function.ToDoubleFunction<TripOption> f) {
        return l.stream().mapToDouble(f).max().orElse(0);
    }

    // ---------- Output ----------
    static String fmtTime(double hours) {
        int h = (int) hours;
        int m = (int) Math.round((hours - h) * 60);
        if (m == 60) { h++; m = 0; }
        return h + "h " + String.format("%02d", m) + "m";
    }

    static void print(String title, List<TripOption> options) {
        System.out.println("\n== " + title + " ==");
        System.out.printf("%-8s %6s %12s %12s %10s %12s %8s%n",
                "Mode", "Units", "Cost/person", "Total cost", "Time", "CO2/person", "Score");
        System.out.println("-".repeat(76));
        for (TripOption o : options) {
            System.out.printf("%-8s %6d %12.2f %12.2f %10s %12.2f %8.3f%n",
                    o.mode.label, o.units, o.costPerPerson, o.cost, fmtTime(o.hours),
                    o.emissionsPerPerson, o.score);
        }
    }

    static void report(List<TripOption> options) {
        List<TripOption> byCost = new ArrayList<>(options);
        byCost.sort(Comparator.comparingDouble(o -> o.costPerPerson));
        print("Sorted by COST (cheapest first)", byCost);

        List<TripOption> byTime = new ArrayList<>(options);
        byTime.sort(Comparator.comparingDouble(o -> o.hours));
        print("Sorted by TIME (fastest first)", byTime);

        List<TripOption> byEmis = new ArrayList<>(options);
        byEmis.sort(Comparator.comparingDouble(o -> o.emissionsPerPerson));
        print("Sorted by EMISSIONS (greenest first)", byEmis);
    }

    // ---------- Main (console UI) ----------
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("=== Trip Planner: compare cost, time and emissions ===");

        double distance = readDouble(in, "Trip distance (km): ", 0.1, 20000);
        int travellers = (int) readDouble(in, "Number of travellers: ", 1, 100);

        int seatsPerCar = (int) readDouble(in,
                "Passengers per car (including driver, 1-7): ", 1, 7);

        List<TripOption> options = plan(distance, travellers, seatsPerCar);
        if (options.isEmpty()) {
            System.out.println("No transport mode supports that distance.");
            return;
        }

        System.out.println("\nHow much does each factor matter to you? (any positive numbers, they are normalised)");
        double wc = readDouble(in, "Weight for COST: ", 0, 1000);
        double wt = readDouble(in, "Weight for TIME: ", 0, 1000);
        double we = readDouble(in, "Weight for EMISSIONS: ", 0, 1000);
        double sum = wc + wt + we;
        if (sum == 0) { wc = wt = we = 1; sum = 3; }

        score(options, wc / sum, wt / sum, we / sum);

        report(options);

        List<TripOption> ranked = new ArrayList<>(options);
        ranked.sort(Comparator.comparingDouble(o -> o.score));
        print("OVERALL RANKING (weighted score, lower is better)", ranked);

        TripOption best = ranked.get(0);
        System.out.printf("%nRecommended: %s (%.2f per person, %s, %.2f kg CO2 per person)%n",
                best.mode.label, best.costPerPerson, fmtTime(best.hours), best.emissionsPerPerson);

        TripOption worstCo2 = Collections.max(options, Comparator.comparingDouble(o -> o.emissionsPerPerson));
        double saved = worstCo2.emissionsPerPerson - best.emissionsPerPerson;
        if (saved > 0) {
            System.out.printf("Choosing it instead of %s saves %.2f kg CO2 per person.%n", worstCo2.mode.label, saved);
        }
    }

    static double readDouble(Scanner in, String prompt, double lo, double hi) {
        while (true) {
            System.out.print(prompt);
            try {
                double v = Double.parseDouble(in.nextLine().trim());
                if (v >= lo && v <= hi) return v;
                System.out.println("Enter a value between " + lo + " and " + hi + ".");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }
}
