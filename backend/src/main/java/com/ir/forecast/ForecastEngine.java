package com.ir.forecast;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ForecastEngine {
    private static final BigDecimal ALPHA = BigDecimal.valueOf(0.3);
    private static final BigDecimal BETA = BigDecimal.valueOf(0.1);
    private static final int WINDOW = 7;
    private static final int SEASONAL_PERIOD = 7;

    @Data
    public static class Point {
        private LocalDate date;
        private BigDecimal qty;
        private BigDecimal lower;
        private BigDecimal upper;

        public Point() {
        }

        public Point(LocalDate date, BigDecimal qty) {
            this.date = date;
            this.qty = qty;
        }
    }

    @Data
    public static class Result {
        private List<BigDecimal> history = new ArrayList<>();
        private List<BigDecimal> forecastValues = new ArrayList<>();
        private List<Point> forecast = new ArrayList<>();
        private String method;
        private double mape;
        private List<Object> backtest = new ArrayList<>();
    }

    public List<BigDecimal> movingAverage(
            List<BigDecimal> history,
            int horizon) {
        List<BigDecimal> values = new ArrayList<>(history);
        List<BigDecimal> forecast = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            int start = Math.max(0, values.size() - WINDOW);
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (int j = start; j < values.size(); j++) {
                sum = sum.add(values.get(j));
                count++;
            }
            BigDecimal next = count == 0
                    ? BigDecimal.ZERO
                    : sum.divide(BigDecimal.valueOf(count), 6,
                    RoundingMode.HALF_UP);
            forecast.add(next);
            values.add(next);
        }
        return forecast;
    }

    public List<BigDecimal> ses(
            List<BigDecimal> history,
            int horizon) {
        BigDecimal level = history.isEmpty()
                ? BigDecimal.ZERO : history.get(0);
        for (int i = 1; i < history.size(); i++) {
            level = ALPHA.multiply(history.get(i))
                    .add(BigDecimal.ONE.subtract(ALPHA).multiply(level));
        }
        List<BigDecimal> forecast = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            forecast.add(level);
        }
        return forecast;
    }

    public List<BigDecimal> holt(
            List<BigDecimal> history,
            int horizon) {
        if (history.isEmpty()) {
            return zeros(horizon);
        }
        BigDecimal level = history.get(0);
        BigDecimal trend = history.size() > 1
                ? history.get(1).subtract(history.get(0))
                : BigDecimal.ZERO;
        for (int i = 1; i < history.size(); i++) {
            BigDecimal previousLevel = level;
            level = ALPHA.multiply(history.get(i))
                    .add(BigDecimal.ONE.subtract(ALPHA)
                            .multiply(level.add(trend)));
            trend = BETA.multiply(level.subtract(previousLevel))
                    .add(BigDecimal.ONE.subtract(BETA).multiply(trend));
        }
        List<BigDecimal> forecast = new ArrayList<>();
        for (int i = 1; i <= horizon; i++) {
            forecast.add(level.add(trend.multiply(BigDecimal.valueOf(i))));
        }
        return forecast;
    }

    public List<BigDecimal> seasonalNaive(
            List<BigDecimal> history,
            int horizon) {
        List<BigDecimal> forecast = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            int target = history.size() + i - SEASONAL_PERIOD;
            List<BigDecimal> values = new ArrayList<>();
            for (int week = 0; week < 4; week++) {
                int index = target - week * SEASONAL_PERIOD;
                if (index >= 0 && index < history.size()) {
                    values.add(history.get(index));
                }
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal value : values) {
                sum = sum.add(value);
            }
            forecast.add(values.isEmpty()
                    ? BigDecimal.ZERO
                    : sum.divide(BigDecimal.valueOf(values.size()), 6,
                    RoundingMode.HALF_UP));
        }
        return forecast;
    }

    public Result forecast(
            List<BigDecimal> history,
            int horizon,
            String requestedMethod) {
        Result result = new Result();
        result.setHistory(new ArrayList<>(history));
        String method = requestedMethod == null ? "AUTO" : requestedMethod;
        if ("AUTO".equals(method)) {
            method = selectMethod(history);
        }
        List<BigDecimal> values = run(history, horizon, method);
        result.setMethod(method);
        result.setForecastValues(values);
        result.setMape(backtest(history, method));
        result.getBacktest().add(method);
        result.getBacktest().add(result.getMape());
        double deviation = residualStd(history, method);
        BigDecimal band = BigDecimal.valueOf(1.28 * deviation);
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            Point point = new Point(LocalDate.now().plusDays(i + 1), value);
            point.setLower(value.subtract(band).max(BigDecimal.ZERO));
            point.setUpper(value.add(band));
            points.add(point);
        }
        result.setForecast(points);
        return result;
    }

    public List<BigDecimal> run(
            List<BigDecimal> history,
            int horizon,
            String method) {
        if ("MA".equals(method)) {
            return movingAverage(history, horizon);
        }
        if ("SES".equals(method)) {
            return ses(history, horizon);
        }
        if ("HOLT".equals(method)) {
            return holt(history, horizon);
        }
        return seasonalNaive(history, horizon);
    }

    public double backtest(
            List<BigDecimal> history,
            String method) {
        if (history.size() < 8) {
            return 0;
        }
        int holdout = Math.min(14, Math.max(1, history.size() / 3));
        int start = history.size() - holdout;
        double error = 0;
        int count = 0;
        for (int i = start; i < history.size(); i++) {
            List<BigDecimal> training = history.subList(0, i);
            BigDecimal predicted = run(training, 1, method).get(0);
            double actual = history.get(i).doubleValue();
            if (actual != 0) {
                error += Math.abs(predicted.doubleValue() - actual)
                        / Math.abs(actual);
                count++;
            }
        }
        return count == 0 ? 0 : error / count * 100;
    }

    private String selectMethod(List<BigDecimal> history) {
        double bestError = Double.MAX_VALUE;
        String bestMethod = "MA";
        for (String method : Arrays.asList(
                "MA", "SES", "HOLT", "SEASONAL_NAIVE")) {
            double error = backtest(history, method);
            if (error < bestError) {
                bestError = error;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    private double residualStd(
            List<BigDecimal> history,
            String method) {
        if (history.size() < 8) {
            return 0;
        }
        int start = Math.max(1, history.size() - 14);
        List<Double> residuals = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            BigDecimal predicted = run(history.subList(0, i), 1, method)
                    .get(0);
            residuals.add(history.get(i).subtract(predicted).doubleValue());
        }
        double average = 0;
        for (Double residual : residuals) {
            average += residual;
        }
        average /= Math.max(1, residuals.size());
        double sum = 0;
        for (Double residual : residuals) {
            sum += Math.pow(residual - average, 2);
        }
        return Math.sqrt(sum / Math.max(1, residuals.size()));
    }

    private List<BigDecimal> zeros(int horizon) {
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            result.add(BigDecimal.ZERO);
        }
        return result;
    }
}
