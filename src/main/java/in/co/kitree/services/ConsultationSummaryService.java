package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.pojos.OnDemandConsultationOrder;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for generating AI-powered consultation summaries.
 * Currently uses mock data; will integrate with LLM in future.
 *
 * Summary schema includes:
 * - headline, brief_summary, primary_concern, sentiment
 * - important_dates: array of {date, significance, is_auspicious}
 * - topics: array of {id, category, title, summary, expert_advice}
 * - predictions: array of {id, category, prediction_text, timeframe, likelihood, astrological_factors}
 * - remedies: array of {id, type, title, description, purpose, priority, timing, frequency, ...type-specific fields}
 * - insights: array of {id, category, text, planetary_influence}
 * - follow_up: {recommended, timeframe, reason, same_expert_recommended}
 */
public class ConsultationSummaryService {

    private final Firestore db;
    private final OnDemandConsultationService consultationService;
    private final StreamService streamService;

    public ConsultationSummaryService(Firestore db, boolean isTest) {
        this.db = db;
        this.consultationService = new OnDemandConsultationService(db);
        this.streamService = new StreamService(isTest);
    }

    /**
     * Result object for summary generation.
     */
    public static class SummaryResult {
        public final boolean success;
        public final Map<String, Object> summary;
        public final String errorMessage;

        private SummaryResult(boolean success, Map<String, Object> summary, String errorMessage) {
            this.success = success;
            this.summary = summary;
            this.errorMessage = errorMessage;
        }

        public static SummaryResult success(Map<String, Object> summary) {
            return new SummaryResult(true, summary, null);
        }

        public static SummaryResult error(String message) {
            return new SummaryResult(false, null, message);
        }
    }

    /**
     * Generate consultation summary for a completed order.
     * Currently returns mock data; will integrate with LLM in future.
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     * @return SummaryResult with generated summary or error
     */
    public SummaryResult generateSummary(String userId, String orderId) {
        try {
            LoggingService.setContext(userId, orderId, null);
            LoggingService.info("generate_summary_started");

            // 1. Get the order
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                return SummaryResult.error("Order not found");
            }

            // 2. Verify order is COMPLETED
            if (!"COMPLETED".equals(order.getStatus())) {
                return SummaryResult.error("Order is not completed. Current status: " + order.getStatus());
            }

            // 3. Check if summary already exists
            if (order.getSummary() != null) {
                LoggingService.info("summary_already_exists");
                return SummaryResult.success(order.getSummary());
            }

            // 4. Get recording info (stubbed for now)
            // Future: List<StreamService.RecordingInfo> recordings = streamService.getCallRecordings(...)

            // 5. Generate summary (mock for now, will call LLM in future)
            Map<String, Object> summary = generateMockSummary(order);

            // 6. Store summary in order document
            consultationService.updateOrderSummary(userId, orderId, summary);

            LoggingService.info("summary_generated_successfully");
            return SummaryResult.success(summary);

        } catch (Exception e) {
            LoggingService.error("generate_summary_error", e, Map.of("orderId", orderId));
            return SummaryResult.error(e.getMessage());
        }
    }

    /**
     * Get existing summary for an order without regenerating.
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     * @return SummaryResult with existing summary or error
     */
    public SummaryResult getSummary(String userId, String orderId) {
        try {
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                return SummaryResult.error("Order not found");
            }

            Map<String, Object> summary = order.getSummary();
            if (summary == null) {
                return SummaryResult.error("Summary not available");
            }

            return SummaryResult.success(summary);

        } catch (Exception e) {
            LoggingService.error("get_summary_error", e, Map.of("orderId", orderId));
            return SummaryResult.error(e.getMessage());
        }
    }

    /**
     * Generate summary asynchronously (fire and forget).
     * Used after billing completion to not block the transaction.
     */
    public void generateSummaryAsync(String userId, String orderId) {
        // In Lambda, we run synchronously but catch errors to not fail billing
        try {
            generateSummary(userId, orderId);
        } catch (Exception e) {
            LoggingService.warn("async_summary_generation_failed", Map.of(
                "orderId", orderId,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Generate mock summary based on order category.
     * This will be replaced with actual LLM call in future.
     */
    private Map<String, Object> generateMockSummary(OnDemandConsultationOrder order) {
        Map<String, Object> summary = new HashMap<>();

        // Metadata
        summary.put("generated_at", Timestamp.now());
        summary.put("language", "en");

        // Get category for context-aware mock data
        String category = order.getCategory() != null ? order.getCategory() : "HOROSCOPE";

        // Overview
        summary.put("headline", getMockHeadline(category));
        summary.put("brief_summary", getMockBriefSummary(category));
        summary.put("primary_concern", getMockPrimaryConcern(category));
        summary.put("sentiment", "positive");

        // Important dates
        summary.put("important_dates", getMockImportantDates());

        // Topics discussed
        summary.put("topics", getMockTopics(category));

        // Predictions
        summary.put("predictions", getMockPredictions(category));

        // Remedies
        summary.put("remedies", getMockRemedies(category));

        // Insights
        summary.put("insights", getMockInsights(category));

        // Follow-up recommendation
        summary.put("follow_up", getMockFollowUp());

        return summary;
    }

    private String getMockHeadline(String category) {
        switch (category.toUpperCase()) {
            case "TAROT":
                return "Tarot guidance for relationship and personal growth";
            case "NUMEROLOGY":
                return "Numerology analysis for career opportunities";
            case "PALMISTRY":
                return "Palmistry reading for life path insights";
            default:
                return "Career guidance for upcoming job transition";
        }
    }

    private String getMockBriefSummary(String category) {
        switch (category.toUpperCase()) {
            case "TAROT":
                return "Discussed relationship dynamics and personal development path. Cards indicate positive changes ahead in emotional connections. Recommended focusing on self-care and setting healthy boundaries.";
            case "NUMEROLOGY":
                return "Analyzed life path number and current year influences. Numbers suggest a favorable period for career advancement starting mid-year. Advised on lucky dates for important decisions.";
            case "PALMISTRY":
                return "Examined major lines and mounts for life insights. Palm indicates strong intellectual abilities and upcoming travel opportunities. Discussed health precautions for coming months.";
            default:
                return "Discussed career change from IT to consulting. Jupiter mahadasha starting in April 2024 brings positive changes. Recommended waiting until after April for major decisions.";
        }
    }

    private String getMockPrimaryConcern(String category) {
        switch (category.toUpperCase()) {
            case "TAROT":
                return "Relationship guidance";
            case "NUMEROLOGY":
                return "Career timing";
            case "PALMISTRY":
                return "Life path clarity";
            default:
                return "Career change";
        }
    }

    private List<Map<String, Object>> getMockImportantDates() {
        List<Map<String, Object>> dates = new ArrayList<>();

        Map<String, Object> date1 = new HashMap<>();
        date1.put("date", "April 2024");
        date1.put("significance", "Jupiter Mahadasha begins");
        date1.put("is_auspicious", true);
        dates.add(date1);

        Map<String, Object> date2 = new HashMap<>();
        date2.put("date", "May 15, 2024");
        date2.put("significance", "Auspicious for new beginnings");
        date2.put("is_auspicious", true);
        dates.add(date2);

        Map<String, Object> date3 = new HashMap<>();
        date3.put("date", "July 2024");
        date3.put("significance", "Saturn transit completion");
        date3.put("is_auspicious", true);
        dates.add(date3);

        return dates;
    }

    private List<Map<String, Object>> getMockTopics(String category) {
        List<Map<String, Object>> topics = new ArrayList<>();

        Map<String, Object> topic1 = new HashMap<>();
        topic1.put("id", "topic_1");
        topic1.put("category", "career");
        topic1.put("title", "Job Transition");
        topic1.put("summary", "You discussed switching from IT to consulting. Current role satisfaction is low.");
        topic1.put("expert_advice", "Wait until after April before making any major career changes.");
        topics.add(topic1);

        Map<String, Object> topic2 = new HashMap<>();
        topic2.put("id", "topic_2");
        topic2.put("category", "finance");
        topic2.put("title", "Investment Planning");
        topic2.put("summary", "Discussed investment options for next 6 months.");
        topic2.put("expert_advice", "Avoid risky investments until Saturn transit completes in July.");
        topics.add(topic2);

        Map<String, Object> topic3 = new HashMap<>();
        topic3.put("id", "topic_3");
        topic3.put("category", "health");
        topic3.put("title", "Wellness Focus");
        topic3.put("summary", "Discussed stress management and work-life balance.");
        topic3.put("expert_advice", "Practice morning meditation and avoid late nights.");
        topics.add(topic3);

        return topics;
    }

    private List<Map<String, Object>> getMockPredictions(String category) {
        List<Map<String, Object>> predictions = new ArrayList<>();

        Map<String, Object> pred1 = new HashMap<>();
        pred1.put("id", "pred_1");
        pred1.put("category", "career");
        pred1.put("prediction_text", "Positive career growth expected after April 2024. New opportunities will arise in consulting domain.");
        pred1.put("timeframe", "April 2024 - October 2024");
        pred1.put("likelihood", "highly_likely");
        pred1.put("astrological_factors", Arrays.asList("Jupiter Mahadasha", "10th house activation"));
        predictions.add(pred1);

        Map<String, Object> pred2 = new HashMap<>();
        pred2.put("id", "pred_2");
        pred2.put("category", "finance");
        pred2.put("prediction_text", "Financial stability improves in second half of 2024.");
        pred2.put("timeframe", "July 2024 onwards");
        pred2.put("likelihood", "likely");
        pred2.put("astrological_factors", Arrays.asList("Saturn transit completion", "2nd house Jupiter aspect"));
        predictions.add(pred2);

        Map<String, Object> pred3 = new HashMap<>();
        pred3.put("id", "pred_3");
        pred3.put("category", "relationships");
        pred3.put("prediction_text", "Favorable period for family harmony and social connections.");
        pred3.put("timeframe", "May 2024 - August 2024");
        pred3.put("likelihood", "likely");
        pred3.put("astrological_factors", Arrays.asList("Venus transit in 7th house"));
        predictions.add(pred3);

        return predictions;
    }

    private List<Map<String, Object>> getMockRemedies(String category) {
        List<Map<String, Object>> remedies = new ArrayList<>();

        // Mantra remedy
        Map<String, Object> remedy1 = new HashMap<>();
        remedy1.put("id", "remedy_1");
        remedy1.put("type", "mantra");
        remedy1.put("title", "Hanuman Chalisa");
        remedy1.put("description", "Chant daily for protection and strength during transition period");
        remedy1.put("purpose", "To strengthen Mars and gain courage");
        remedy1.put("priority", "essential");
        remedy1.put("timing", "Early morning after bath");
        remedy1.put("frequency", "Daily");
        remedy1.put("mantra_text", "Full Hanuman Chalisa");
        remedy1.put("repetition_count", 1);
        remedies.add(remedy1);

        // Product remedy (gemstone)
        Map<String, Object> remedy2 = new HashMap<>();
        remedy2.put("id", "remedy_2");
        remedy2.put("type", "product");
        remedy2.put("title", "Yellow Sapphire Ring");
        remedy2.put("description", "Wear a certified yellow sapphire in gold ring on index finger");
        remedy2.put("purpose", "To strengthen Jupiter for career growth");
        remedy2.put("priority", "recommended");
        remedy2.put("timing", "Wear on Thursday morning");
        remedy2.put("product_type", "gemstone");
        Map<String, Object> productAttrs = new HashMap<>();
        productAttrs.put("material", "Yellow Sapphire");
        productAttrs.put("color", "Yellow");
        productAttrs.put("metal", "Gold");
        productAttrs.put("weight_range", "3-5 carats");
        remedy2.put("product_attributes", productAttrs);
        remedies.add(remedy2);

        // Puja remedy
        Map<String, Object> remedy3 = new HashMap<>();
        remedy3.put("id", "remedy_3");
        remedy3.put("type", "puja");
        remedy3.put("title", "Brihaspati Puja");
        remedy3.put("description", "Perform Jupiter puja for career blessings");
        remedy3.put("purpose", "To appease Jupiter and enhance career prospects");
        remedy3.put("priority", "recommended");
        remedy3.put("timing", "Any Thursday");
        remedy3.put("puja_type", "Brihaspati Shanti Puja");
        remedies.add(remedy3);

        // Donation remedy
        Map<String, Object> remedy4 = new HashMap<>();
        remedy4.put("id", "remedy_4");
        remedy4.put("type", "donation");
        remedy4.put("title", "Feed Brahmins");
        remedy4.put("description", "Donate yellow items and feed Brahmins on Thursdays");
        remedy4.put("purpose", "To strengthen Jupiter blessings");
        remedy4.put("priority", "optional");
        remedy4.put("timing", "Every Thursday");
        remedy4.put("frequency", "Weekly");
        remedies.add(remedy4);

        // Lifestyle remedy
        Map<String, Object> remedy5 = new HashMap<>();
        remedy5.put("id", "remedy_5");
        remedy5.put("type", "lifestyle");
        remedy5.put("title", "Morning Meditation");
        remedy5.put("description", "Practice 15 minutes of morning meditation facing east");
        remedy5.put("purpose", "To calm the mind and enhance decision-making clarity");
        remedy5.put("priority", "essential");
        remedy5.put("timing", "Sunrise");
        remedy5.put("frequency", "Daily");
        remedies.add(remedy5);

        return remedies;
    }

    private List<Map<String, Object>> getMockInsights(String category) {
        List<Map<String, Object>> insights = new ArrayList<>();

        Map<String, Object> insight1 = new HashMap<>();
        insight1.put("id", "insight_1");
        insight1.put("category", "general");
        insight1.put("text", "Strong Jupiter influence in your chart indicates natural leadership abilities");
        insight1.put("planetary_influence", "Jupiter in 10th house");
        insights.add(insight1);

        Map<String, Object> insight2 = new HashMap<>();
        insight2.put("id", "insight_2");
        insight2.put("category", "timing");
        insight2.put("text", "Current Saturn transit is creating temporary delays, will clear by July");
        insight2.put("planetary_influence", "Saturn transit through 4th house");
        insights.add(insight2);

        Map<String, Object> insight3 = new HashMap<>();
        insight3.put("id", "insight_3");
        insight3.put("category", "strength");
        insight3.put("text", "Your chart shows excellent communication skills and analytical thinking");
        insight3.put("planetary_influence", "Mercury in 3rd house exalted");
        insights.add(insight3);

        return insights;
    }

    private Map<String, Object> getMockFollowUp() {
        Map<String, Object> followUp = new HashMap<>();
        followUp.put("recommended", true);
        followUp.put("timeframe", "After 3 months");
        followUp.put("reason", "To review career progress after Jupiter mahadasha begins");
        followUp.put("same_expert_recommended", true);
        return followUp;
    }
}
