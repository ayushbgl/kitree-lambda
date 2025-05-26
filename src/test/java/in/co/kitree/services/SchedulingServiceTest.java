package in.co.kitree.services;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulingServiceTest {

    @Test
    public void testGetExpertAvailabilitySlots() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "08:00");
        timeSlot.put("endTime", "09:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();

        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Asia/Kolkata";

        String rangeStart = "2035-02-04"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-02-06";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-02-04");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-02-05");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-02-06");

        assertEquals(3, availabilitySlots.size());
        assertEquals("unavailable", sundaySlot.getStatus());
        assertEquals("available", mondaySlot.getStatus());
        assertEquals("unavailable", tuesdaySlot.getStatus());
        assertEquals(2, mondaySlot.getSpots().size());
        assertEquals("08:00", mondaySlot.getSpots().get(0).getStartTime());
        assertEquals("08:30", mondaySlot.getSpots().get(0).getEndTime());
        assertEquals("08:30", mondaySlot.getSpots().get(1).getStartTime());
        assertEquals("09:00", mondaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testDifferentTimeZones() {

        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "12:00");
        timeSlot.put("endTime", "13:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();


        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Europe/Zurich";

        String rangeStart = "2035-02-04"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-02-06";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-02-04");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-02-05");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-02-06");

        assertEquals(3, availabilitySlots.size());
        assertEquals("unavailable", sundaySlot.getStatus());
        assertEquals("available", mondaySlot.getStatus());
        assertEquals("unavailable", tuesdaySlot.getStatus());
        assertEquals(2, mondaySlot.getSpots().size());
        assertEquals("07:30", mondaySlot.getSpots().get(0).getStartTime());
        assertEquals("08:00", mondaySlot.getSpots().get(0).getEndTime());
        assertEquals("08:00", mondaySlot.getSpots().get(1).getStartTime());
        assertEquals("08:30", mondaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testDifferentTimeZoneWithDST() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "12:00");
        timeSlot.put("endTime", "13:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();


        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Europe/Zurich";

        String rangeStart = "2035-06-03"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-06-05";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-06-03");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-06-04");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-06-05");

        assertEquals(3, availabilitySlots.size());
        assertEquals("unavailable", sundaySlot.getStatus());
        assertEquals("available", mondaySlot.getStatus());
        assertEquals("unavailable", tuesdaySlot.getStatus());
        assertEquals(2, mondaySlot.getSpots().size());
        assertEquals("08:30", mondaySlot.getSpots().get(0).getStartTime());
        assertEquals("09:00", mondaySlot.getSpots().get(0).getEndTime());
        assertEquals("09:00", mondaySlot.getSpots().get(1).getStartTime());
        assertEquals("09:30", mondaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testDifferentTimeZoneWithMidnight() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "01:00");
        timeSlot.put("endTime", "02:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();


        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Europe/Zurich";

        String rangeStart = "2035-06-03"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-06-05";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-06-03");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-06-04");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-06-05");

        assertEquals(3, availabilitySlots.size());
        assertEquals("available", sundaySlot.getStatus());
        assertEquals("unavailable", mondaySlot.getStatus());
        assertEquals("unavailable", tuesdaySlot.getStatus());
        assertEquals(2, sundaySlot.getSpots().size());
        assertEquals(0, mondaySlot.getSpots().size());
        assertEquals("21:30", sundaySlot.getSpots().get(0).getStartTime());
        assertEquals("22:00", sundaySlot.getSpots().get(0).getEndTime());
        assertEquals("22:00", sundaySlot.getSpots().get(1).getStartTime());
        assertEquals("22:30", sundaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testDifferentTimeZoneWithMidnightMultipleDays() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "02:30");
        timeSlot.put("endTime", "04:30");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();

        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Europe/Zurich";

        String rangeStart = "2035-06-03"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-06-05";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-06-03");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-06-04");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-06-05");

        assertEquals(3, availabilitySlots.size());
        assertEquals("available", sundaySlot.getStatus());
        assertEquals("available", mondaySlot.getStatus());
        assertEquals("unavailable", tuesdaySlot.getStatus());
        assertEquals(2, sundaySlot.getSpots().size());
        assertEquals(2, mondaySlot.getSpots().size());
        assertEquals("23:00", sundaySlot.getSpots().get(0).getStartTime());
        assertEquals("23:30", sundaySlot.getSpots().get(0).getEndTime());
        assertEquals("23:30", sundaySlot.getSpots().get(1).getStartTime());
        assertEquals("00:00", sundaySlot.getSpots().get(1).getEndTime()); // TODO
        assertEquals("00:00", mondaySlot.getSpots().get(0).getStartTime());
        assertEquals("00:30", mondaySlot.getSpots().get(0).getEndTime());
        assertEquals("00:30", mondaySlot.getSpots().get(1).getStartTime());
        assertEquals("01:00", mondaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testDifferentTimeZoneWithMidnightMultipleDaysNextDayNotRequested() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "22:00");
        timeSlot.put("endTime", "23:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();

        String availabilityTimeZone = "Europe/Zurich";
        String userTimeZone = "Asia/Kolkata";

        String rangeStart = "2035-06-03"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-06-04";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-06-03");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-06-04");

        assertEquals(2, availabilitySlots.size());
        assertEquals("unavailable", sundaySlot.getStatus());
        assertEquals("unavailable", mondaySlot.getStatus());
        assertEquals(0, sundaySlot.getSpots().size());
        assertEquals(0, mondaySlot.getSpots().size());
    }

    @Test
    public void testDifferentTimeZoneWithMidnightMultipleDaysNextDay() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "22:00");
        timeSlot.put("endTime", "23:00");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();

        String availabilityTimeZone = "Europe/Zurich";
        String userTimeZone = "Asia/Kolkata";

        String rangeStart = "2035-06-03"; // Future date because old slots are not considered from current time TODO: Mock current date received for testing.
        String rangeEnd = "2035-06-05";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot sundaySlot = availabilitySlots.get("2035-06-03");
        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-06-04");
        SchedulingService.AvailabilitySlot tuesdaySlot = availabilitySlots.get("2035-06-05");

        assertEquals(3, availabilitySlots.size());
        assertEquals("unavailable", sundaySlot.getStatus());
        assertEquals("unavailable", mondaySlot.getStatus());
        assertEquals("available", tuesdaySlot.getStatus());
        assertEquals(0, sundaySlot.getSpots().size());
        assertEquals(0, mondaySlot.getSpots().size());
        assertEquals(2, tuesdaySlot.getSpots().size());
        assertEquals("01:30", tuesdaySlot.getSpots().get(0).getStartTime());
        assertEquals("02:00", tuesdaySlot.getSpots().get(0).getEndTime());
        assertEquals("02:00", tuesdaySlot.getSpots().get(1).getStartTime());
        assertEquals("02:30", tuesdaySlot.getSpots().get(1).getEndTime());
    }

    @Test
    public void testGetExpertAvailabilitySlotsExistingBookings() {
        Map<String, Map<String, List<Map<String, String>>>> expertAvailability = new HashMap<>();
        Map<String, List<Map<String, String>>> mondayAvailability = new HashMap<>();
        List<Map<String, String>> timeSlots = new ArrayList<>();
        Map<String, String> timeSlot = new HashMap<>();
        timeSlot.put("startTime", "08:00");
        timeSlot.put("endTime", "09:30");
        timeSlots.add(timeSlot);
        mondayAvailability.put("timeSlots", timeSlots);
        expertAvailability.put("MONDAY", mondayAvailability);

        List<Map<String, Object>> overrides = new ArrayList<>();
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();
        Map<String, ZonedDateTime> booking = new HashMap<>();
        booking.put("startTime", ZonedDateTime.of(2035, 2, 5, 8, 30, 0, 0, ZoneId.of("Asia/Kolkata")));
        booking.put("endTime", ZonedDateTime.of(2035, 2, 5, 9, 0, 0, 0, ZoneId.of("Asia/Kolkata")));
        existingBookings.add(booking);
        String availabilityTimeZone = "Asia/Kolkata";
        String userTimeZone = "Asia/Kolkata";

        String rangeStart = "2035-02-05";
        String rangeEnd = "2035-02-05";

        int durationOfSlot = 30;
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        int startTimeIncrementInMinutes = 30;

        SchedulingService schedulingService = new SchedulingService();
        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = schedulingService.getExpertAvailabilitySlots(expertAvailability, overrides, existingBookings, availabilityTimeZone, rangeStart, rangeEnd, userTimeZone, durationOfSlot, durationUnit, startTimeIncrementInMinutes);

        SchedulingService.AvailabilitySlot mondaySlot = availabilitySlots.get("2035-02-05");

        assertEquals(1, availabilitySlots.size());
        assertEquals("available", mondaySlot.getStatus());
        assertEquals(2, mondaySlot.getSpots().size());
        assertEquals("08:00", mondaySlot.getSpots().get(0).getStartTime());
        assertEquals("08:30", mondaySlot.getSpots().get(0).getEndTime());
        assertEquals("09:00", mondaySlot.getSpots().get(1).getStartTime());
        assertEquals("09:30", mondaySlot.getSpots().get(1).getEndTime());
    }
}
