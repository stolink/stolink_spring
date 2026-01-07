package com.stolink.backend.domain.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingActivity {
    private Map<String, Long> byDayOfWeek;    // mon, tue, wed, thu, fri, sat, sun
    private Map<String, Long> byTimeOfDay;    // morning, afternoon, evening, night
    private List<DailyStats> last30Days;
}
