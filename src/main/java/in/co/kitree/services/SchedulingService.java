package in.co.kitree.services;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SchedulingService {
    public SchedulingService() {
    }

    public final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public class Interval {
        public int startTime;
        public int endTime;

        public Interval(int startTime, int endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    public class AvailabilitySlot {
        String status = "unavailable";
        List<Spot> spots = new ArrayList<>();

        public AvailabilitySlot() {

        }

        public String getStatus() {
            return status;
        }

        public List<Spot> getSpots() {
            return spots;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public class Spot {
        public Spot(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        private String startTime;
        private String endTime;

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }
    }

    public enum DurationUnit {
        MINUTES, HOURS
    }

    private Map<String, Object> findOverrideForDate(LocalDate date, List<Map<String, Object>> overrides) {
        for (Map<String, Object> override : overrides) {
            String overrideDate = (String) override.get("date");
            if (overrideDate != null && LocalDate.parse(overrideDate, dateFormatter).isEqual(date)) {
                return override;
            }
        }
        return null;
    }

    public Map<String, AvailabilitySlot> getExpertAvailabilitySlots(
            Map<String, Map<String, List<Map<String, String>>>> expertAvailability,
            List<Map<String, Object>> overrides,
            List<Map<String, ZonedDateTime>> existingBookings,
            String availabilityTimezone,
            String rangeStart,
            String rangeEnd,
            String userTimeZone,
            long durationOfSlot,
            DurationUnit durationUnit,
            long startTimeIncrementInMinutes
    ) {
        List<Map<String, ZonedDateTime>> results = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(rangeStart, dateFormatter).minusDays(1);
        LocalDate endDate = LocalDate.parse(rangeEnd, dateFormatter).plusDays(1);

        ZoneId expertZone = ZoneId.of(availabilityTimezone);

        while (startDate.isBefore(endDate.plusDays(1))) {
            String dayOfWeek = startDate.getDayOfWeek().toString().toUpperCase(); // Convert to uppercase for matching
            List<Map<String, String>> timeSlots = getAvailableTimeSlots(expertAvailability, dayOfWeek, startDate, overrides, existingBookings);
            for (Map<String, String> timeSlot : timeSlots) {
                LocalTime expertStartTime = LocalTime.parse(timeSlot.get("startTime"));
                LocalTime expertEndTime = LocalTime.parse(timeSlot.get("endTime"));

                ZonedDateTime expertStart = ZonedDateTime.of(startDate, expertStartTime, expertZone);
                ZonedDateTime expertEnd = ZonedDateTime.of(startDate, expertEndTime, expertZone);

                if (expertStart.isBefore(expertEnd)) {
                    while (expertStart.isBefore(expertEnd.minusMinutes(durationOfSlot - 1))) {
                        Map<String, ZonedDateTime> slotMap = new HashMap<>();
                        ZonedDateTime slotEndTime = expertStart.plus(durationOfSlot, durationUnit == DurationUnit.MINUTES ? ChronoUnit.MINUTES : ChronoUnit.HOURS); // TODO

                        slotMap.put("startTime", expertStart);
                        slotMap.put("endTime", slotEndTime);
                        results.add(slotMap);

                        expertStart = expertStart.plusMinutes(startTimeIncrementInMinutes);
                    }
                }
            }

            startDate = startDate.plusDays(1);
        }

        results = removeExistingBookingsSlots(results, existingBookings);
        return convertToUserTimeZone(
                results,
                userTimeZone,
                rangeStart,
                rangeEnd
        );
    }

    private List<Map<String, ZonedDateTime>> removeExistingBookingsSlots(List<Map<String, ZonedDateTime>> appointmentSlots, List<Map<String, ZonedDateTime>> existingBookings) {
        List<Map<String, ZonedDateTime>> availableSlots = new ArrayList<>();
        for (Map<String, ZonedDateTime> appointmentSlot : appointmentSlots) {
            ZonedDateTime appointmentStart = appointmentSlot.get("startTime");
            ZonedDateTime appointmentEnd = appointmentSlot.get("endTime");
            boolean isAvailable = true;
            for (Map<String, ZonedDateTime> existingBooking : existingBookings) {
                ZonedDateTime existingStart = existingBooking.get("startTime");
                ZonedDateTime existingEnd = existingBooking.get("endTime");
                // Check for overlap
                if (isOverlapping(appointmentStart, appointmentEnd, existingStart, existingEnd)) {
                    isAvailable = false;
                    break;
                }
            }
            if (isAvailable) {
                availableSlots.add(appointmentSlot);
            }
        }
        return availableSlots;
    }

    private static boolean isOverlapping(ZonedDateTime start1, ZonedDateTime end1, ZonedDateTime start2, ZonedDateTime end2) {
        // Check if appointment start falls within existing booking window
        return start1.isEqual(start2) ||
                end1.isEqual(end2) ||
                (start1.isBefore(end2) && start1.isAfter(start2)) ||
                // Check if appointment end falls within existing booking window
                (end1.isBefore(end2) && end1.isAfter(start2)) ||
                // Check if existing booking entirely overlaps appointment slot
                (start1.isBefore(start2) && end1.isAfter(end2));
    }

    private Map<String, AvailabilitySlot> convertToUserTimeZone(
            List<Map<String, ZonedDateTime>> slotsInExpertTimeZone,
            String userTimeZone,
            String rangeStart,
            String rangeEnd) {
        Map<String, AvailabilitySlot> output = new HashMap<>();
        LocalDate startDate = LocalDate.parse(rangeStart, dateFormatter);
        LocalDate endDate = LocalDate.parse(rangeEnd, dateFormatter);
        ZonedDateTime userRangeStart = ZonedDateTime.of(startDate, LocalTime.MIN, ZoneId.of(userTimeZone));
        ZonedDateTime userRangeEnd = ZonedDateTime.of(endDate, LocalTime.MAX, ZoneId.of(userTimeZone));

        for (LocalDate date = startDate; date.isBefore(endDate.plusDays(1)); date = date.plusDays(1)) {
            output.put(date.format(dateFormatter), new AvailabilitySlot());
        }

        for (Map<String, ZonedDateTime> slot : slotsInExpertTimeZone) {

            ZonedDateTime slotStart = slot.get("startTime");
            ZonedDateTime slotEnd = slot.get("endTime");

            if (slot.get("startTime").getDayOfWeek() != slot.get("endTime").getDayOfWeek()) {
                continue; // TODO: If slot starts and ends on different days, we need to handle this
            }

            ZonedDateTime currentTime = ZonedDateTime.now();

            if (slotStart.isBefore(currentTime)) {
                continue;
            }

            if (slotStart.isAfter(userRangeStart.minusMinutes(1)) && slotEnd.isBefore(userRangeEnd.plusMinutes(1))) {
                slotStart = slotStart.withZoneSameInstant(ZoneId.of(userTimeZone));
                slotEnd = slotEnd.withZoneSameInstant(ZoneId.of(userTimeZone));

                String date = slotStart.format(dateFormatter);
                output.get(date).setStatus("available");
                output.get(date).getSpots().add(new Spot(slotStart.format(timeFormatter), slotEnd.format(timeFormatter)));
            }
        }
        return output;
    }


    private List<Map<String, String>> getAvailableTimeSlots(
            Map<String, Map<String, List<Map<String, String>>>> expertAvailability,
            String dayOfWeek,
            LocalDate currentDate,
            List<Map<String, Object>> overrides,
            List<Map<String, ZonedDateTime>> existingBookings
    ) {
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, Object> override = findOverrideForDate(currentDate, overrides);
        if (override != null) {
            timeSlots = (List<Map<String, String>>) override.get("timeSlots");
        } else if (expertAvailability.containsKey(dayOfWeek)) {
            Map<String, List<Map<String, String>>> dayAvailability = expertAvailability.get(dayOfWeek);
            timeSlots = dayAvailability.get("timeSlots");
        }

        return timeSlots;
    }

    public int convertTimeToMinutes(String timeStr) {
        String[] parts = timeStr.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid time format. Expected HH:MM");
        }

        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        if (hours < 0 || hours >= 24 || minutes < 0 || minutes >= 60) {
            throw new IllegalArgumentException("Invalid time values. Hours must be between 0 and 23, minutes between 0 and 59");
        }

        return hours * 60 + minutes;
    }

    public static String convertMinutesToTime(int minutes) {
        if (minutes < 0 || minutes >= 24 * 60) {
            throw new IllegalArgumentException("Invalid minutes value. Must be between 0 and 1439");
        }

        int hours = minutes / 60;
        int minutesPart = minutes % 60;

        return String.format("%02d:%02d", hours, minutesPart);
    }

    public List<Interval> removeIntervals(List<Interval> intervals, List<Interval> toRemove) {
        // Sort intervals by start time for efficient comparison
        intervals.sort(Comparator.comparingInt(i -> i.startTime));
        toRemove.sort(Comparator.comparingInt(i -> i.startTime));

        List<Interval> remainingIntervals = new ArrayList<>();
        int i = 0, j = 0;

        while (i < intervals.size() && j < toRemove.size()) {
            Interval interval = intervals.get(i);
            Interval removeInterval = toRemove.get(j);

            // Case 1: Interval ends before removal entirely
            if (interval.endTime <= removeInterval.startTime) {
                remainingIntervals.add(interval);
                i++;
            } else if (interval.startTime >= removeInterval.endTime) {
                // Case 2: Removal ends before interval entirely
                j++;
            } else {
                // Case 3: Overlapping intervals
                int newStart = Math.max(interval.startTime, removeInterval.startTime);
                int newEnd = Math.min(interval.endTime, removeInterval.endTime);

                // Add remaining parts of the original interval before overlap
                if (newStart > interval.startTime) {
                    remainingIntervals.add(new Interval(interval.startTime, newStart));
                }

                // Add remaining parts of the original interval after overlap (if any)
                if (newEnd < interval.endTime) {
                    remainingIntervals.add(new Interval(newEnd, interval.endTime));
                }

                i++;
            }
        }

        // Add any remaining intervals after the removals have ended
        remainingIntervals.addAll(intervals.subList(i, intervals.size()));

        return remainingIntervals;
    }
}
