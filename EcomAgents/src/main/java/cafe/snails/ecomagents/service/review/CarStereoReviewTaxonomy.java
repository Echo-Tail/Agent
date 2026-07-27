package cafe.snails.ecomagents.service.review;

import java.util.Set;

public final class CarStereoReviewTaxonomy {
    public static final String VERSION = "car_stereo_v1";
    public static final Set<String> USAGE_SCENARIOS = Set.of(
            "installation_setup", "daily_commute", "carplay_android_auto", "bluetooth_call",
            "music_audio", "navigation", "reverse_parking", "night_driving", "cold_start",
            "long_trip", "firmware_update", "after_sales", "other");
    public static final Set<String> PRODUCT_MODULES = Set.of(
            "vehicle_compatibility", "wiring_installation", "display_touch", "carplay",
            "android_auto", "bluetooth_wifi", "audio_dsp_microphone", "gps_navigation",
            "reverse_camera", "ui_interaction", "performance_stability", "hardware_reliability",
            "firmware_update", "documentation_support", "other");
    public static final Set<String> SEVERITIES = Set.of("critical", "major", "moderate", "minor");
    public static final Set<String> SENTIMENTS = Set.of("negative", "mixed", "positive");
    public static final Set<String> ACTION_TYPES = Set.of(
            "firmware", "hardware", "accessory", "docs", "qa", "support");

    private CarStereoReviewTaxonomy() {}
}
