package io.malicki.bankingsystem.api;

import io.malicki.bankingsystem.kafka.consumer.DltMonitorConsumer;
import io.malicki.bankingsystem.kafka.errorhandling.DeadLetterTopicService;
import io.malicki.bankingsystem.kafka.errorhandling.ErrorCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dlt")
@Slf4j
public class DltStatsController {
    
    private final DltMonitorConsumer dltMonitorConsumer;
    private final DeadLetterTopicService dltService;
    
    public DltStatsController(
        DltMonitorConsumer dltMonitorConsumer,
        DeadLetterTopicService dltService
    ) {
        this.dltMonitorConsumer = dltMonitorConsumer;
        this.dltService = dltService;
    }
    
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total messages sent to DLT
        stats.put("totalSentToDlt", dltService.getDltMessageCount());
        
        // By topic
        Map<String, Long> byTopic = dltMonitorConsumer.getDltCountByTopic()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get()
            ));
        stats.put("byTopic", byTopic);
        
        // By category
        Map<String, Long> byCategory = dltMonitorConsumer.getDltCountByCategory()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                e -> e.getKey().name(),
                e -> e.getValue().get()
            ));
        stats.put("byCategory", byCategory);
        
        return stats;
    }
    
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        long totalDlt = dltService.getDltMessageCount();
        
        health.put("status", totalDlt < 100 ? "HEALTHY" : "WARNING");
        health.put("totalDltMessages", totalDlt);
        health.put("threshold", 100);
        
        if (totalDlt >= 100) {
            health.put("alert", "High number of DLT messages - investigation required!");
        }
        
        return health;
    }
}